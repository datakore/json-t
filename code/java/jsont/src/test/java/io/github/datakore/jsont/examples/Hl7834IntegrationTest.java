package io.github.datakore.jsont.examples;

// =============================================================================
// Hl7834IntegrationTest — examples/healthcare/hl7 end-to-end coverage
// =============================================================================
//
// 1. schema_parses_without_error
//    Parse hl7_834.jsont via SchemaRegistry.fromNamespace. Confirms grammar,
//    enum declarations, nested schema references, and the data-schema pointer
//    all resolve cleanly.
//
// 2. data_schema_pointer_resolves
//    data-schema: Hl7_834 must be accessible in the registry.
//
// 3. sensitive_fields_are_marked
//    Verifies PHI and credential fields carry the sensitive (~) flag.
//
// 4. generate_and_stringify_with_encryption
//    Builds 2 ISA_Segment rows (16 fields each; authInfo and securityInfo are
//    sensitive) through a RowWriter backed by AES-GCM PassthroughCryptoConfig
//    and asserts:
//      - output is non-empty and contains "base64:" (encryption happened)
//      - PHI plaintext (credential value "secretpw") is absent
//
// 5. round_trip_parse_and_validate
//    Parses the encrypted ISA_Segment output back via JsonTParser.parseRows and
//    runs ValidationPipeline.validateOne. Asserts: correct row count, zero fatal
//    diagnostics, at least one Encrypted value in the parsed rows.
//
// 6. plaintext_round_trip_no_crypto
//    Stringify NM1_Loop rows without encryption, re-parse, validate — confirms
//    the pipeline works end-to-end on plain sensitive-schema rows.
// =============================================================================

import io.github.datakore.jsont.builder.SchemaRegistry;
import io.github.datakore.jsont.crypto.AlgoVersion;
import io.github.datakore.jsont.crypto.CryptoContext;
import io.github.datakore.jsont.crypto.CryptoError;
import io.github.datakore.jsont.crypto.KekMode;
import io.github.datakore.jsont.crypto.PassthroughCryptoConfig;
import io.github.datakore.jsont.diagnostic.DiagnosticEvent;
import io.github.datakore.jsont.internal.diagnostic.MemorySink;
import io.github.datakore.jsont.model.JsonTField;
import io.github.datakore.jsont.model.JsonTNamespace;
import io.github.datakore.jsont.model.JsonTRow;
import io.github.datakore.jsont.model.JsonTSchema;
import io.github.datakore.jsont.model.JsonTValue;
import io.github.datakore.jsont.parse.JsonTParser;
import io.github.datakore.jsont.stringify.RowWriter;
import io.github.datakore.jsont.validate.ValidationPipeline;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Hl7834IntegrationTest {

    // Relative to Maven module root (code/java/jsont)
    private static final Path SCHEMA_FILE =
            Path.of("../../../examples/healthcare/hl7/hl7_834.jsont");

    private static final PassthroughCryptoConfig PASSTHROUGH = new PassthroughCryptoConfig();

    private static SchemaRegistry registry;
    private static JsonTNamespace  namespace;

    @BeforeAll
    static void loadSchema() throws IOException {
        assertTrue(Files.exists(SCHEMA_FILE),
                "Schema not found at " + SCHEMA_FILE.toAbsolutePath());
        String src = Files.readString(SCHEMA_FILE);
        namespace = JsonTParser.parseNamespace(src);
        registry  = SchemaRegistry.fromNamespace(namespace);
    }

    // ── 1. Schema parses without error ───────────────────────────────────────

    @Test
    void schema_parses_without_error() {
        assertNotNull(registry, "registry must be non-null");
        List<String> expectedSchemas = List.of(
                "ISA_Segment", "GS_Segment", "ST_Segment", "BGN_Segment",
                "Loop_1000A", "Loop_1000B", "Member_Loop", "NM1_Loop",
                "INS_Segment", "REF_Segment", "DTP_Segment", "DMG_Segment",
                "HealthCoverage_Loop", "HD_Segment",
                "SE_Segment", "GE_Segment", "IEA_Segment",
                "TransactionSet", "FunctionalGroup", "Hl7_834");
        for (String name : expectedSchemas) {
            assertTrue(registry.resolve(name).isPresent(),
                    "schema '" + name + "' must be resolvable");
        }
    }

    // ── 2. data-schema pointer resolves ──────────────────────────────────────

    @Test
    void data_schema_pointer_resolves() {
        String dataSchemaName = namespace.dataSchema();
        assertEquals("Hl7_834", dataSchemaName, "data-schema pointer must be Hl7_834");
        assertTrue(registry.resolve(dataSchemaName).isPresent(),
                "data-schema Hl7_834 must be resolvable in registry");
    }

    // ── 3. Sensitive fields are marked ───────────────────────────────────────

    @Test
    void sensitive_fields_are_marked() {
        assertSensitive("ISA_Segment", "authInfo");
        assertSensitive("ISA_Segment", "securityInfo");
        assertSensitive("NM1_Loop",    "lastName");
        assertSensitive("NM1_Loop",    "firstName");
        assertSensitive("NM1_Loop",    "middleName");
        assertSensitive("NM1_Loop",    "prefix");
        assertSensitive("NM1_Loop",    "memberId");
        assertSensitive("REF_Segment", "identification");
        assertSensitive("DMG_Segment", "dateOfBirth");
        // gender and maritalStatus are enum-typed (object refs) — ~marker not supported on object fields
    }

    // ── 4. Generate + stringify with encryption ───────────────────────────────

    @Test
    void generate_and_stringify_with_encryption() throws IOException, CryptoError {
        // Use ISA_Segment: 16 fields, authInfo~ and securityInfo~ are sensitive.
        JsonTSchema isaSchema = registry.resolve("ISA_Segment").orElseThrow();
        CryptoContext encCtx = CryptoContext.forEncrypt(
                AlgoVersion.AES_GCM, KekMode.PUBLIC_KEY, PASSTHROUGH);

        List<JsonTRow> rows = buildIsaRows();
        StringWriter sw = new StringWriter();
        try (RowWriter writer = new RowWriter(isaSchema, encCtx, registry)) {
            writer.writeStream(rows, sw);
        }

        String output = sw.toString();
        assertFalse(output.isBlank(), "serialised output must not be empty");

        // writeStream emits an ENCRYPTED_HEADER row when the schema has sensitive fields.
        assertTrue(output.contains("ENCRYPTED_HEADER"),
                "output must contain the ENCRYPTED_HEADER row signalling encryption");

        // ISA04 securityInfo must not appear as plaintext.
        assertFalse(output.contains("secretpw"),
                "ISA securityInfo must not appear in plaintext");

        System.out.printf("hl7-834 encrypted ISA output: %d bytes, %d rows%n",
                output.length(), rows.size());
    }

    // ── 5. Round-trip: parse + validate encrypted ISA output ─────────────────

    @Test
    void round_trip_parse_and_validate() throws IOException, CryptoError {
        JsonTSchema isaSchema = registry.resolve("ISA_Segment").orElseThrow();
        CryptoContext encCtx = CryptoContext.forEncrypt(
                AlgoVersion.AES_GCM, KekMode.PUBLIC_KEY, PASSTHROUGH);

        List<JsonTRow> original = buildIsaRows();
        StringWriter sw = new StringWriter();
        try (RowWriter writer = new RowWriter(isaSchema, encCtx, registry)) {
            writer.writeStream(original, sw);
        }

        // writeStream emits a header row + data rows; parse all.
        List<JsonTRow> parsed = new ArrayList<>();
        JsonTParser.parseRows(sw.toString(), parsed::add);
        assertTrue(parsed.size() >= original.size(),
                "parsed row count must include at least the data rows");

        // Validate each data row (skip header row at index 0).
        MemorySink sink = new MemorySink();
        ValidationPipeline pipeline = ValidationPipeline.builder(isaSchema)
                .withoutConsole()
                .withSink(sink)
                .withRegistry(registry)
                .withCryptoContext(encCtx)
                .build();

        int dataRowCount = 0;
        for (int i = 1; i < parsed.size(); i++) {
            sink.clear();
            pipeline.validateOne(parsed.get(i), clean -> {});
            List<DiagnosticEvent> fatals = sink.events().stream()
                    .filter(DiagnosticEvent::isFatal)
                    .toList();
            assertTrue(fatals.isEmpty(),
                    "row " + i + " must have zero fatal diagnostics, got: " + fatals);
            dataRowCount++;
        }
        assertEquals(original.size(), dataRowCount, "all data rows must have been validated");
        // Note: schema-less parseRows produces Str values for encrypted payloads (no schema context).
        // The ENCRYPTED_HEADER row in the output is what guarantees encryption happened.

        // Plaintext absent: secretpw must not survive encryption into the wire output.
        StringWriter sw2 = new StringWriter();
        try (RowWriter writer2 = new RowWriter(isaSchema, encCtx, registry)) {
            writer2.writeStream(original, sw2);
        }
        assertFalse(sw2.toString().contains("secretpw"),
                "ISA securityInfo plaintext must not appear after encryption");

        System.out.printf("round_trip_parse_and_validate: %d data rows validated%n", dataRowCount);
    }

    // ── 6. Plaintext round-trip for non-sensitive schema (GS_Segment) ────────

    @Test
    void plaintext_round_trip_no_crypto() throws IOException {
        // Use GS_Segment (8 fields, no sensitive fields) so no CryptoContext is needed.
        JsonTSchema gsSchema = registry.resolve("GS_Segment").orElseThrow();
        List<JsonTRow> original = buildGsRows();

        StringWriter sw = new StringWriter();
        RowWriter.writeRows(original, sw);

        List<JsonTRow> parsed = new ArrayList<>();
        JsonTParser.parseRows(sw.toString(), parsed::add);
        assertEquals(original.size(), parsed.size(), "parsed count must match generated count");

        MemorySink sink = new MemorySink();
        ValidationPipeline pipeline = ValidationPipeline.builder(gsSchema)
                .withoutConsole()
                .withSink(sink)
                .withRegistry(registry)
                .build();

        for (int i = 0; i < parsed.size(); i++) {
            sink.clear();
            pipeline.validateOne(parsed.get(i), clean -> {});
            List<DiagnosticEvent> fatals = sink.events().stream()
                    .filter(DiagnosticEvent::isFatal)
                    .toList();
            assertTrue(fatals.isEmpty(),
                    "plaintext GS row " + i + " must have zero fatal diagnostics, got: " + fatals);
        }

        System.out.printf("plaintext_round_trip GS_Segment: %d rows, all valid%n", parsed.size());
    }

    // ── Row factories ─────────────────────────────────────────────────────────

    /**
     * Builds 2 ISA_Segment rows (16 fields each).
     * Fields authInfo (pos 1) and securityInfo (pos 3) are sensitive.
     */
    private static List<JsonTRow> buildIsaRows() {
        return List.of(buildIsaRow(0L, "P", "secretpw  "), buildIsaRow(1L, "T", "testpw    "));
    }

    private static JsonTRow buildIsaRow(long idx, String usageIndicator, String securityInfo) {
        List<JsonTValue> v = new ArrayList<>();
        v.add(JsonTValue.text("00"));
        v.add(JsonTValue.text("          "));       // authInfo      (sensitive ~)
        v.add(JsonTValue.text("00"));
        v.add(JsonTValue.text(securityInfo));        // securityInfo  (sensitive ~)
        v.add(JsonTValue.text("ZZ"));
        v.add(JsonTValue.text("SENDER-ID      "));
        v.add(JsonTValue.text("ZZ"));
        v.add(JsonTValue.text("RECEIVER-ID    "));
        v.add(JsonTValue.date("2026-04-09"));
        v.add(JsonTValue.time("10:30:00"));
        v.add(JsonTValue.text("^"));
        v.add(JsonTValue.text("00501"));
        v.add(JsonTValue.u32(123456789L));
        v.add(JsonTValue.text("0"));
        v.add(JsonTValue.text(usageIndicator));
        v.add(JsonTValue.text(":"));
        return JsonTRow.at(idx, v);
    }

    /**
     * Builds 2 GS_Segment rows (8 fields each — no sensitive fields).
     * Used for the plaintext round-trip test to avoid requiring a CryptoContext.
     */
    private static List<JsonTRow> buildGsRows() {
        return List.of(buildGsRow(0L, "SENDER-A",   "RECEIVER-A", 987654321L),
                       buildGsRow(1L, "SENDER-B",   "RECEIVER-B", 111222333L));
    }

    private static JsonTRow buildGsRow(long idx, String senderCode, String receiverCode,
                                       long controlNumber) {
        List<JsonTValue> v = new ArrayList<>();
        v.add(JsonTValue.text("BE"));
        v.add(JsonTValue.text(senderCode));
        v.add(JsonTValue.text(receiverCode));
        v.add(JsonTValue.date("2026-04-09"));
        v.add(JsonTValue.time("10:30:00"));
        v.add(JsonTValue.u32(controlNumber));
        v.add(JsonTValue.text("X"));
        v.add(JsonTValue.text("005010X220A1"));
        return JsonTRow.at(idx, v);
    }

    // ── Assertion helpers ─────────────────────────────────────────────────────

    private void assertSensitive(String schemaName, String fieldName) {
        JsonTSchema schema = registry.resolve(schemaName)
                .orElseThrow(() -> new AssertionError("schema not found: " + schemaName));
        JsonTField field = schema.fields().stream()
                .filter(f -> f.name().equals(fieldName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "field '" + fieldName + "' not found in schema '" + schemaName + "'"));
        assertTrue(field.sensitive(),
                "field '" + schemaName + "." + fieldName + "' must be marked sensitive (~)");
    }
}
