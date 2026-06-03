package io.github.datakore.jsont.internal.transform;

import io.github.datakore.jsont.builder.SchemaResolver;
import io.github.datakore.jsont.crypto.CryptoContext;
import io.github.datakore.jsont.crypto.CryptoError;
import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.internal.transform.handler.FieldRefCollector;
import io.github.datakore.jsont.model.EvalContext;
import io.github.datakore.jsont.model.FieldPath;
import io.github.datakore.jsont.model.JsonTExpression;
import io.github.datakore.jsont.model.JsonTRow;
import io.github.datakore.jsont.model.JsonTSchema;
import io.github.datakore.jsont.model.JsonTValue;
import io.github.datakore.jsont.model.RenamePair;
import io.github.datakore.jsont.model.SchemaOperation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parse-time execution descriptor for a derived schema.
 *
 * <p>Built once when {@link io.github.datakore.jsont.transform.RowTransformer} is
 * constructed; applied O(1) per row using pre-computed position indices. Eliminates
 * per-row {@code resolveEffectiveFields} chain-walking, {@link FieldRefCollector} AST
 * traversal, and O(F) working-map scans that the legacy path incurred on every row.
 */
public final class ResolvedTransform {

    // ── Step sealed hierarchy ─────────────────────────────────────────────────

    public sealed interface Step
            permits Step.Rename, Step.Reshape, Step.Filter, Step.Transform, Step.Decrypt {

        /**
         * Rename step: no-op on values at runtime — subsequent steps already reference
         * post-rename names as their binding keys. Kept as a marker so step count matches
         * the operation list.
         */
        record Rename() implements Step {}

        /**
         * Reshape step: keeps only the listed positions in the listed order.
         * Handles both {@code Project} and {@code Exclude} operations.
         */
        record Reshape(int[] keepPositions) implements Step {}

        /**
         * Filter step: evaluates a predicate from pre-computed bindings.
         * Throws {@link JsonTError.Transform.Filtered} when the row should be dropped.
         */
        record Filter(int[] bindingPositions, String[] bindingKeys,
                      JsonTExpression predicate) implements Step {}

        /**
         * Transform step: evaluates an expression and replaces the value at
         * {@code targetPosition}.
         */
        record Transform(int targetPosition, int[] bindingPositions, String[] bindingKeys,
                         JsonTExpression expr) implements Step {}

        /** Decrypt step: decrypts encrypted values at the listed positions in-place. */
        record Decrypt(int[] positions, String[] fieldNames) implements Step {}
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final int parentFieldCount;
    private final List<Step> steps;

    private ResolvedTransform(int parentFieldCount, List<Step> steps) {
        this.parentFieldCount = parentFieldCount;
        this.steps = List.copyOf(steps);
    }

    public int parentFieldCount() { return parentFieldCount; }
    public List<Step> steps()     { return steps; }

    // ── Build-time factory ────────────────────────────────────────────────────

    /**
     * Builds a {@code ResolvedTransform} for the given derived schema.
     * Called once at {@link io.github.datakore.jsont.transform.RowTransformer}
     * construction time; never called per row.
     *
     * @throws JsonTError.Transform if the parent schema is unknown, a field path
     *                              is not found, or a cyclic derivation is detected
     */
    public static ResolvedTransform build(JsonTSchema schema, SchemaResolver registry)
            throws JsonTError.Transform {

        String from = schema.derivedFrom()
                .orElseThrow(() -> new JsonTError.Transform("not a derived schema: " + schema.name()));
        JsonTSchema parent = registry.resolve(from)
                .orElseThrow(() -> new JsonTError.Transform.UnknownSchema(from));

        List<String> chain = new ArrayList<>(List.of(schema.name()));
        List<String> parentFieldNames = OperationApplicator.resolveEffectiveFields(parent, registry, chain);

        // current[i] = the current logical name of slot i; mutated by Rename/Reshape
        // so that each subsequent step resolves positions against the post-op state.
        List<String> current = new ArrayList<>(parentFieldNames);

        List<Step> steps = new ArrayList<>();
        for (SchemaOperation op : schema.operations()) {
            steps.add(buildStep(op, current));
        }

        return new ResolvedTransform(parentFieldNames.size(), steps);
    }

    // ── Step builders (build-time only) ───────────────────────────────────────

    private static Step buildStep(SchemaOperation op, List<String> current)
            throws JsonTError.Transform {
        if (op instanceof SchemaOperation.Rename r)    return buildRenameStep(r, current);
        if (op instanceof SchemaOperation.Exclude ex)  return buildReshapeFromExclude(ex, current);
        if (op instanceof SchemaOperation.Project pr)  return buildReshapeFromProject(pr, current);
        if (op instanceof SchemaOperation.Filter f)    return buildFilterStep(f, current);
        if (op instanceof SchemaOperation.Transform t) return buildTransformStep(t, current);
        if (op instanceof SchemaOperation.Decrypt d)   return buildDecryptStep(d, current);
        throw new JsonTError.Transform("Unknown SchemaOperation type: " + op.getClass().getSimpleName());
    }

    private static Step.Rename buildRenameStep(SchemaOperation.Rename r, List<String> current)
            throws JsonTError.Transform {
        for (RenamePair pair : r.pairs()) {
            String from = pair.from().dotJoined();
            int pos = indexOf(current, from);
            if (pos < 0) throw new JsonTError.Transform.FieldNotFound(from);
            current.set(pos, pair.to());   // update working names for subsequent steps
        }
        return new Step.Rename();
    }

    private static Step.Reshape buildReshapeFromExclude(SchemaOperation.Exclude ex,
                                                         List<String> current)
            throws JsonTError.Transform {
        Set<String> excluded = new HashSet<>();
        for (FieldPath path : ex.paths()) {
            String name = path.dotJoined();
            if (indexOf(current, name) < 0) throw new JsonTError.Transform.FieldNotFound(name);
            excluded.add(name);
        }
        List<Integer> keep = new ArrayList<>();
        for (int i = 0; i < current.size(); i++) {
            if (!excluded.contains(current.get(i))) keep.add(i);
        }
        int[] keepArr = keep.stream().mapToInt(Integer::intValue).toArray();
        List<String> retained = new ArrayList<>();
        for (int k : keepArr) retained.add(current.get(k));
        current.clear();
        current.addAll(retained);
        return new Step.Reshape(keepArr);
    }

    private static Step.Reshape buildReshapeFromProject(SchemaOperation.Project pr,
                                                         List<String> current)
            throws JsonTError.Transform {
        int[] keepArr = new int[pr.paths().size()];
        List<String> retained = new ArrayList<>(pr.paths().size());
        for (int i = 0; i < pr.paths().size(); i++) {
            String name = pr.paths().get(i).dotJoined();
            int pos = indexOf(current, name);
            if (pos < 0) throw new JsonTError.Transform.FieldNotFound(name);
            keepArr[i] = pos;
            retained.add(current.get(pos));
        }
        current.clear();
        current.addAll(retained);
        return new Step.Reshape(keepArr);
    }

    private static Step.Filter buildFilterStep(SchemaOperation.Filter f, List<String> current)
            throws JsonTError.Transform {
        List<String> refs = FieldRefCollector.collect(f.predicate());
        int[] bpos  = new int[refs.size()];
        String[] bkeys = new String[refs.size()];
        for (int i = 0; i < refs.size(); i++) {
            int pos = indexOf(current, refs.get(i));
            if (pos < 0) throw new JsonTError.Transform.FieldNotFound(refs.get(i));
            bpos[i]  = pos;
            bkeys[i] = refs.get(i);
        }
        return new Step.Filter(bpos, bkeys, f.predicate());
    }

    private static Step.Transform buildTransformStep(SchemaOperation.Transform t,
                                                      List<String> current)
            throws JsonTError.Transform {
        String target = t.target().dotJoined();
        int tp = indexOf(current, target);
        if (tp < 0) throw new JsonTError.Transform.FieldNotFound(target);
        List<String> refs = FieldRefCollector.collect(t.expr());
        int[] bpos  = new int[refs.size()];
        String[] bkeys = new String[refs.size()];
        for (int i = 0; i < refs.size(); i++) {
            int pos = indexOf(current, refs.get(i));
            if (pos < 0) throw new JsonTError.Transform.FieldNotFound(refs.get(i));
            bpos[i]  = pos;
            bkeys[i] = refs.get(i);
        }
        return new Step.Transform(tp, bpos, bkeys, t.expr());
    }

    private static Step.Decrypt buildDecryptStep(SchemaOperation.Decrypt d,
                                                   List<String> current)
            throws JsonTError.Transform {
        int[] positions  = new int[d.fields().size()];
        String[] names   = new String[d.fields().size()];
        for (int i = 0; i < d.fields().size(); i++) {
            String name = d.fields().get(i);
            int pos = indexOf(current, name);
            if (pos < 0) throw new JsonTError.Transform.FieldNotFound(name);
            positions[i] = pos;
            names[i]     = name;
        }
        return new Step.Decrypt(positions, names);
    }

    // ── Per-row application ───────────────────────────────────────────────────

    /**
     * Applies all pre-computed steps to a row's values using O(1) positional access.
     *
     * @param row the input row; value count must match {@link #parentFieldCount()}
     * @param ctx crypto context required for any {@link Step.Decrypt} step; {@code null} otherwise
     * @return the transformed row (index and schema preserved)
     * @throws JsonTError.Transform          on structural or expression-evaluation failure
     * @throws JsonTError.Transform.Filtered when a Filter step drops this row
     */
    public JsonTRow apply(JsonTRow row, CryptoContext ctx) throws JsonTError.Transform {
        if (row.values().size() != parentFieldCount) {
            throw new JsonTError.Transform.FieldNotFound(
                    "row has " + row.values().size() + " values but schema expects " + parentFieldCount);
        }
        JsonTValue[] working = row.values().toArray(new JsonTValue[0]);

        for (Step step : steps) {
            working = applyStep(step, working, ctx);
        }

        return row.withValues(Arrays.asList(working));
    }

    // ── Step applicators (per-row hot path) ───────────────────────────────────

    private static JsonTValue[] applyStep(Step step, JsonTValue[] working, CryptoContext ctx)
            throws JsonTError.Transform {
        if (step instanceof Step.Rename) {
            return working; // no-op on values
        }
        if (step instanceof Step.Reshape r) {
            JsonTValue[] next = new JsonTValue[r.keepPositions().length];
            for (int i = 0; i < r.keepPositions().length; i++) {
                next[i] = working[r.keepPositions()[i]];
            }
            return next;
        }
        if (step instanceof Step.Filter f) {
            EvalContext evalCtx = buildContext(f.bindingPositions(), f.bindingKeys(), working);
            try {
                JsonTValue result = f.predicate().evaluate(evalCtx);
                if (result instanceof JsonTValue.Bool b) {
                    if (!b.value()) throw new JsonTError.Transform.Filtered();
                    return working;
                }
                throw new JsonTError.Transform(
                        "filter expression returned non-boolean: "
                        + result.getClass().getSimpleName());
            } catch (JsonTError.Eval e) {
                throw new JsonTError.Transform("filter evaluation failed: " + e.getMessage(), e);
            }
        }
        if (step instanceof Step.Transform t) {
            EvalContext evalCtx = buildContext(t.bindingPositions(), t.bindingKeys(), working);
            try {
                working[t.targetPosition()] = t.expr().evaluate(evalCtx);
                return working;
            } catch (JsonTError.Eval e) {
                throw new JsonTError.Transform(
                        "transform failed at position " + t.targetPosition()
                        + ": " + e.getMessage(), e);
            }
        }
        if (step instanceof Step.Decrypt d) {
            if (ctx == null) throw new JsonTError.Transform.DecryptFailed("",
                    "Decrypt step requires a CryptoContext; "
                    + "use transformWithContext instead of transform");
            for (int i = 0; i < d.positions().length; i++) {
                int pos     = d.positions()[i];
                String name = d.fieldNames()[i];
                JsonTValue current = working[pos];
                if (current instanceof JsonTValue.Encrypted) {
                    try {
                        String text = current.decryptStr(name, ctx)
                                .orElseThrow(() -> new JsonTError.Transform.DecryptFailed(
                                        name, "Encrypted value returned empty"));
                        working[pos] = JsonTValue.text(text);
                    } catch (CryptoError e) {
                        throw new JsonTError.Transform.DecryptFailed(name, e.getMessage());
                    }
                }
                // already plaintext — idempotent, skip
            }
            return working;
        }
        throw new JsonTError.Transform("Unknown Step type: " + step.getClass().getSimpleName());
    }

    /** Builds an EvalContext from pre-computed positional bindings — O(refs), not O(fields). */
    private static EvalContext buildContext(int[] positions, String[] keys, JsonTValue[] working) {
        EvalContext ctx = EvalContext.create();
        for (int i = 0; i < positions.length; i++) {
            ctx.bind(keys[i], working[positions[i]]);
        }
        return ctx;
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private static int indexOf(List<String> names, String target) {
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i).equals(target)) return i;
        }
        return -1;
    }
}
