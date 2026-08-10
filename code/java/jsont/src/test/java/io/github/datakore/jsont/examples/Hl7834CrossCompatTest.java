package io.github.datakore.jsont.examples;

// =============================================================================
// Hl7834CrossCompatTest — cross-language HL7 834 schema + data compatibility
// =============================================================================
//
// Mirrors the WCT20 CrossCompatTest pattern but for the HL7 834 schema.
//
// Design:
//   generate_hl7_java  — parse hl7_834.jsont, build rows for GS_Segment and
//                        INS_Segment, write to code/cross-compat/hl7-java-generated.jsont.data
//   validate_hl7_rust  — read hl7-rust-generated.jsont.data (committed by Rust),
//                        parse with the same schema, run ValidationPipeline,
//                        assert zero fatals and spot-check values
//
// Row coverage (both Java and Rust generate 5 rows per schema, same values):
//
//   GS_Segment (8 fields — no sensitive fields, exercises str/date/time/u32):
//     i | senderCode     | receiverCode   | groupControlNumber
//     0 | SENDER-A       | RECEIVER-A     | 100000001
//     1 | SENDER-B       | RECEIVER-B     | 200000002
//     2 | PLAN-SPONSOR-1 | PAYER-1        | 300000003
//     3 | PLAN-SPONSOR-2 | PAYER-2        | 400000004
//     4 | CLEARINGHOUSE  | INSURER-X      | 500000005
//
//   INS_Segment (8 fields — exercises enums and optional nulls):
//     i | memberIndicator | relationshipCode | maintenanceTypeCode | maintenanceReasonCode
//     0 | Y               | SELF             | ADDITION            | null
//     1 | N               | CHILD            | CHANGE              | BIRTH
//     2 | N               | SPOUSE           | CANCELLATION        | DIVORCE
//     3 | N               | EMPLOYEE         | REINSTATEMENT       | ELIG_TERM
//     4 | Y               | SELF             | AUDIT               | null
// =============================================================================

import io.github.datakore.jsont.builder.SchemaRegistry;
import io.github.datakore.jsont.diagnostic.DiagnosticEvent;
import io.github.datakore.jsont.internal.diagnostic.MemorySink;
import io.github.datakore.jsont.model.JsonTNamespace;
import io.github.datakore.jsont.model.JsonTRow;
import io.github.datakore.jsont.model.JsonTSchema;
import io.github.datakore.jsont.model.JsonTValue;
import io.github.datakore.jsont.parse.JsonTParser;
import io.github.datakore.jsont.stringify.RowWriter;
import io.github.datakore.jsont.validate.ValidationPipeline;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Hl7834CrossCompatTest {

    // Relative to Maven module root (code/java/jsont)
    private static final Path SCHEMA_FILE =
            Path.of("../../../examples/healthcare/hl7/hl7_834.jsont");
    private static final Path JAVA_OUT =
            Path.of("../../cross-compat/hl7-java-generated.jsont.data");
    private static final Path RUST_OUT =
            Path.of("../../cross-compat/hl7-rust-generated.jsont.data");

    // GS_Segment field layout (8 fields, position 0-7)
    private static final int GS_SENDER_CODE      = 1;
    private static final int GS_RECEIVER_CODE    = 2;
    private static final int GS_CONTROL_NUMBER   = 5;

    // INS_Segment field layout (8 fields, position 0-7)
    private static final int INS_MEMBER_IND      = 0;
    private static final int INS_RELATIONSHIP    = 1;
    private static final int INS_MAINT_TYPE      = 2;
    private static final int INS_MAINT_REASON    = 3;

    private static SchemaRegistry registry;
    private static JsonTSchema     gsSchema;
    private static JsonTSchema     insSchema;

    @BeforeAll
    static void loadSchema() throws IOException {
        assertTrue(Files.exists(SCHEMA_FILE), "hl7_834.jsont not found");
        String src = Files.readString(SCHEMA_FILE);
        JsonTNamespace ns = JsonTParser.parseNamespace(src);
        registry  = SchemaRegistry.fromNamespace(ns);
        gsSchema  = registry.resolve("GS_Segment").orElseThrow();
        insSchema = registry.resolve("INS_Segment").orElseThrow();
    }

    // ── Generate ──────────────────────────────────────────────────────────────

    /**
     * Generates {@code code/cross-compat/hl7-java-generated.jsont.data}.
     *
     * <p>Layout: 5 GS_Segment rows followed by 5 INS_Segment rows, 10 total.
     * Row indices 0–4 = GS, 5–9 = INS — same order and values as the Rust fixture.
     */
    @Test
    void generate_hl7_java() throws IOException {
        Files.createDirectories(JAVA_OUT.getParent());
        List<JsonTRow> rows = new ArrayList<>();
        rows.addAll(buildGsRows());
        rows.addAll(buildInsRows());

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(JAVA_OUT.toFile()))) {
            RowWriter.writeRows(rows, bw);
        }

        long bytes = Files.size(JAVA_OUT);
        assertEquals(10, rows.size());
        System.out.printf("hl7-java-generated.jsont.data: %d bytes, %d rows%n", bytes, rows.size());
    }

    // ── Validate Rust fixture ─────────────────────────────────────────────────

    /**
     * Reads {@code code/cross-compat/hl7-rust-generated.jsont.data} (committed by the
     * Rust test) and verifies schema conformance and spot-checked values.
     *
     * <p>Run the Rust fixture generator first:
     * <pre>cargo test --test hl7_cross_compat_tests hl7_cross_compat_generate -- --nocapture</pre>
     */
    @Test
    void validate_hl7_rust() throws IOException {
        assertTrue(Files.exists(RUST_OUT),
                "hl7-rust-generated.jsont.data not found — run the Rust hl7 cross-compat test first");

        String content = Files.readString(RUST_OUT);
        List<JsonTRow> rows = new ArrayList<>();
        JsonTParser.parseRows(content, rows::add);
        assertEquals(10, rows.size(), "expected 10 rows from Rust fixture");

        // ── Validate GS rows (0–4) ────────────────────────────────────────────
        MemorySink sink = new MemorySink();
        ValidationPipeline gsPipeline = ValidationPipeline.builder(gsSchema)
                .withoutConsole()
                .withSink(sink)
                .withRegistry(registry)
                .build();

        for (int i = 0; i < 5; i++) {
            sink.clear();
            gsPipeline.validateOne(rows.get(i), clean -> {});
            assertNoFatals(sink.events(), "GS row " + i);
        }

        // ── Spot-check GS rows ────────────────────────────────────────────────
        // Row 0: functionalCode=BE, senderCode=SENDER-A, controlNumber=100000001
        assertText(rows.get(0), 0, "BE");
        assertText(rows.get(0), GS_SENDER_CODE, "SENDER-A");
        assertText(rows.get(0), GS_RECEIVER_CODE, "RECEIVER-A");
        assertNumeric(rows.get(0), GS_CONTROL_NUMBER, 100_000_001.0);

        // Row 2: plan sponsor sender
        assertText(rows.get(2), GS_SENDER_CODE, "PLAN-SPONSOR-1");
        assertText(rows.get(2), GS_RECEIVER_CODE, "PAYER-1");
        assertNumeric(rows.get(2), GS_CONTROL_NUMBER, 300_000_003.0);

        // Row 4: clearinghouse
        assertText(rows.get(4), GS_SENDER_CODE, "CLEARINGHOUSE");

        // ── Validate INS rows (5–9) ───────────────────────────────────────────
        ValidationPipeline insPipeline = ValidationPipeline.builder(insSchema)
                .withoutConsole()
                .withSink(sink)
                .withRegistry(registry)
                .build();

        for (int i = 5; i < 10; i++) {
            sink.clear();
            insPipeline.validateOne(rows.get(i), clean -> {});
            assertNoFatals(sink.events(), "INS row " + i);
        }

        // ── Spot-check INS rows ───────────────────────────────────────────────
        // Row 5 (INS[0]): subscriber self, ADDITION, no reason
        assertText(rows.get(5), INS_MEMBER_IND, "Y");
        assertEnum(rows.get(5), INS_RELATIONSHIP, "SELF");
        assertEnum(rows.get(5), INS_MAINT_TYPE, "ADDITION");
        assertNull(rows.get(5), INS_MAINT_REASON);

        // Row 6 (INS[1]): dependent child, CHANGE, BIRTH
        assertText(rows.get(6), INS_MEMBER_IND, "N");
        assertEnum(rows.get(6), INS_RELATIONSHIP, "CHILD");
        assertEnum(rows.get(6), INS_MAINT_TYPE, "CHANGE");
        assertEnum(rows.get(6), INS_MAINT_REASON, "BIRTH");

        // Row 7 (INS[2]): spouse, CANCELLATION, DIVORCE
        assertEnum(rows.get(7), INS_RELATIONSHIP, "SPOUSE");
        assertEnum(rows.get(7), INS_MAINT_TYPE, "CANCELLATION");
        assertEnum(rows.get(7), INS_MAINT_REASON, "DIVORCE");

        // Row 9 (INS[4]): subscriber self, AUDIT, no reason
        assertText(rows.get(9), INS_MEMBER_IND, "Y");
        assertEnum(rows.get(9), INS_RELATIONSHIP, "SELF");
        assertEnum(rows.get(9), INS_MAINT_TYPE, "AUDIT");
        assertNull(rows.get(9), INS_MAINT_REASON);

        System.out.printf("validate_hl7_rust: all 10 rows valid (5 GS + 5 INS)%n");
    }

    // ── Row builders ─────────────────────────────────────────────────────────

    /** 5 GS_Segment rows (8 fields each). */
    static List<JsonTRow> buildGsRows() {
        String[] senders   = {"SENDER-A",  "SENDER-B",  "PLAN-SPONSOR-1", "PLAN-SPONSOR-2", "CLEARINGHOUSE"};
        String[] receivers = {"RECEIVER-A","RECEIVER-B","PAYER-1",         "PAYER-2",         "INSURER-X"};
        long[]   controls  = {100_000_001L, 200_000_002L, 300_000_003L, 400_000_004L, 500_000_005L};

        List<JsonTRow> rows = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            List<JsonTValue> v = new ArrayList<>();
            v.add(JsonTValue.text("BE"));
            v.add(JsonTValue.text(senders[i]));
            v.add(JsonTValue.text(receivers[i]));
            v.add(JsonTValue.date("2026-04-09"));
            v.add(JsonTValue.time("10:30:00"));
            v.add(JsonTValue.u32(controls[i]));
            v.add(JsonTValue.text("X"));
            v.add(JsonTValue.text("005010X220A1"));
            rows.add(JsonTRow.at(i, v));
        }
        return rows;
    }

    /** 5 INS_Segment rows (8 fields each). */
    static List<JsonTRow> buildInsRows() {
        // memberIndicator, relationshipCode, maintenanceTypeCode, maintenanceReasonCode?,
        // benefitStatusCode?, medicareStatusCode?, consolidatedOmnibus?, employmentStatusCode?
        Object[][] data = {
            {"Y", "SELF",     "ADDITION",    null,        "A", "1", "N", "FT"},
            {"N", "CHILD",    "CHANGE",      "BIRTH",     "A", "1", "N", "FT"},
            {"N", "SPOUSE",   "CANCELLATION","DIVORCE",   "A", "1", "N", "FT"},
            {"N", "EMPLOYEE", "REINSTATEMENT","ELIG_TERM","A", "1", "N", "FT"},
            {"Y", "SELF",     "AUDIT",       null,        "A", "1", "N", "FT"},
        };

        List<JsonTRow> rows = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Object[] d = data[i];
            List<JsonTValue> v = new ArrayList<>();
            v.add(JsonTValue.text((String) d[0]));                           // memberIndicator
            v.add(JsonTValue.enumValue((String) d[1]));                      // relationshipCode
            v.add(JsonTValue.enumValue((String) d[2]));                      // maintenanceTypeCode
            v.add(d[3] == null ? JsonTValue.nullValue()
                               : JsonTValue.enumValue((String) d[3]));       // maintenanceReasonCode?
            v.add(JsonTValue.text((String) d[4]));                           // benefitStatusCode?
            v.add(JsonTValue.text((String) d[5]));                           // medicareStatusCode?
            v.add(JsonTValue.text((String) d[6]));                           // consolidatedOmnibus?
            v.add(JsonTValue.text((String) d[7]));                           // employmentStatusCode?
            rows.add(JsonTRow.at(5 + i, v));
        }
        return rows;
    }

    // ── Assertion helpers ─────────────────────────────────────────────────────

    private static void assertNoFatals(List<DiagnosticEvent> events, String label) {
        List<DiagnosticEvent> fatals = events.stream()
                .filter(DiagnosticEvent::isFatal).toList();
        assertTrue(fatals.isEmpty(), label + " must have zero fatal diagnostics, got: " + fatals);
    }

    private static JsonTValue v(JsonTRow row, int idx) { return row.get(idx); }

    private static void assertText(JsonTRow row, int idx, String expected) {
        var val = v(row, idx);
        assertFalse(val instanceof JsonTValue.Null,
                "field[" + idx + "] expected text, got Null");
        assertEquals(expected, val.asText(), "field[" + idx + "] text mismatch");
    }

    private static void assertNumeric(JsonTRow row, int idx, double expected) {
        var val = v(row, idx);
        assertFalse(val instanceof JsonTValue.Null,
                "field[" + idx + "] expected numeric, got Null");
        assertEquals(expected, val.toDouble(), 1e-3, "field[" + idx + "] numeric mismatch");
    }

    private static void assertEnum(JsonTRow row, int idx, String expected) {
        var val = v(row, idx);
        assertInstanceOf(JsonTValue.Enum.class, val, "field[" + idx + "] expected Enum");
        assertEquals(expected, ((JsonTValue.Enum) val).value(), "field[" + idx + "] enum mismatch");
    }

    private static void assertNull(JsonTRow row, int idx) {
        assertInstanceOf(JsonTValue.Null.class, v(row, idx),
                "field[" + idx + "] expected Null");
    }
}
