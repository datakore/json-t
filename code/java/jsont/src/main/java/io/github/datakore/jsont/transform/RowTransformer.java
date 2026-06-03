package io.github.datakore.jsont.transform;

import io.github.datakore.jsont.builder.SchemaRegistry;
import io.github.datakore.jsont.crypto.CryptoContext;
import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.internal.transform.OperationApplicator;
import io.github.datakore.jsont.internal.transform.ResolvedTransform;
import io.github.datakore.jsont.internal.validate.SchemaValidator;
import io.github.datakore.jsont.model.JsonTRow;
import io.github.datakore.jsont.model.JsonTSchema;
import io.github.datakore.jsont.model.JsonTValue;
import io.github.datakore.jsont.model.SchemaKind;
import io.github.datakore.jsont.model.SchemaOperation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class RowTransformer {

    private final JsonTSchema schema;
    private final SchemaRegistry registry;

    /**
     * Pre-computed execution descriptor built once at construction time.
     * {@code null} for straight schemas (pass-through) or when the registry
     * cannot yet resolve the parent (fallback to legacy per-row path).
     */
    private final ResolvedTransform resolved;

    private RowTransformer(JsonTSchema schema, SchemaRegistry registry) {
        this.schema   = schema;
        this.registry = registry;
        this.resolved = buildResolved(schema, registry);
    }

    public static RowTransformer of(JsonTSchema schema, SchemaRegistry registry) {
        return new RowTransformer(schema, registry);
    }

    /** Attempts to build once; returns null on failure so the legacy path handles it. */
    private static ResolvedTransform buildResolved(JsonTSchema schema, SchemaRegistry registry) {
        if (schema.kind() == SchemaKind.STRAIGHT) return null;
        try {
            return ResolvedTransform.build(schema, registry);
        } catch (JsonTError.Transform ignored) {
            return null;
        }
    }

    // ── Per-row transform ─────────────────────────────────────────────────────

    public JsonTRow transform(JsonTRow row) throws JsonTError.Transform {
        if (schema.kind() == SchemaKind.STRAIGHT) return row;

        // Fast path: O(1) positional access, no per-row chain-walking or AST traversal.
        if (resolved != null) return resolved.apply(row, null);

        // Legacy fallback: used only when registry was built outside from_namespace
        // (e.g. manually assembled test registries that skipped resolve_all).
        return applyLegacy(row, null);
    }

    public JsonTRow transformWithContext(JsonTRow row, CryptoContext ctx)
            throws JsonTError.Transform {
        if (schema.kind() == SchemaKind.STRAIGHT) return row;

        if (resolved != null) return resolved.apply(row, ctx);

        return applyLegacy(row, ctx);
    }

    // ── Legacy per-row path (fallback only) ───────────────────────────────────

    private JsonTRow applyLegacy(JsonTRow row, CryptoContext ctx) throws JsonTError.Transform {
        String from = schema.derivedFrom().get();
        JsonTSchema parent = registry.resolve(from)
                .orElseThrow(() -> new JsonTError.Transform.UnknownSchema(from));

        List<String> chain = new ArrayList<>(List.of(schema.name()));
        List<String> parentFields = OperationApplicator.resolveEffectiveFields(parent, registry, chain);

        if (parentFields.size() != row.values().size()) {
            throw new JsonTError.Transform.FieldNotFound(
                    "row has " + row.values().size() + " values but parent schema has "
                    + parentFields.size() + " output fields");
        }

        LinkedHashMap<String, JsonTValue> working = new LinkedHashMap<>();
        for (int i = 0; i < parentFields.size(); i++) {
            working.put(parentFields.get(i), row.values().get(i));
        }

        for (SchemaOperation op : schema.operations()) {
            working = OperationApplicator.applyOperation(op, working, ctx);
        }

        return JsonTRow.at(row.index(), new ArrayList<>(working.values()));
    }

    // ── Schema-level static validation ───────────────────────────────────────

    public void validateSchema() throws JsonTError.SchemaInvalid {
        LinkedHashMap<String, JsonTSchema> map = new LinkedHashMap<>();
        for (String name : registry.names()) {
            map.put(name, registry.resolveOrThrow(name));
        }
        SchemaValidator.validate(schema, map);
    }
}
