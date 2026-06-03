// =============================================================================
// tests/hl7_cross_compat_tests.rs — HL7 834 schema cross-language compatibility
// =============================================================================
//
// Mirrors the Java Hl7834CrossCompatTest pattern.
//
// Design:
//   hl7_cross_compat_generate — parse hl7_834.jsont, build rows for GS_Segment
//                               and INS_Segment, write to
//                               code/cross-compat/hl7-rust-generated.jsont.data
//   hl7_cross_compat_validate_java — read hl7-java-generated.jsont.data
//                               (committed by the Java test), parse with the
//                               same schema, run ValidationPipeline, assert
//                               zero fatals and spot-check values
//
// Row coverage (must match Java exactly — same values, same row order):
//
//   GS_Segment (8 fields, rows 0–4):
//     i | senderCode     | receiverCode   | groupControlNumber
//     0 | SENDER-A       | RECEIVER-A     | 100000001
//     1 | SENDER-B       | RECEIVER-B     | 200000002
//     2 | PLAN-SPONSOR-1 | PAYER-1        | 300000003
//     3 | PLAN-SPONSOR-2 | PAYER-2        | 400000004
//     4 | CLEARINGHOUSE  | INSURER-X      | 500000005
//
//   INS_Segment (8 fields, rows 5–9):
//     i | memberIndicator | relationshipCode | maintenanceTypeCode | maintenanceReasonCode
//     5 | Y               | SELF             | ADDITION            | null
//     6 | N               | CHILD            | CHANGE              | BIRTH
//     7 | N               | SPOUSE           | CANCELLATION        | DIVORCE
//     8 | N               | EMPLOYEE         | REINSTATEMENT       | ELIG_TERM
//     9 | Y               | SELF             | AUDIT               | null
//
// How to regenerate the committed Rust fixture after schema changes:
//   cargo test --test hl7_cross_compat_tests hl7_cross_compat_generate -- --nocapture
// =============================================================================

use std::fs;
use std::io::{BufWriter, Write};
use std::path::Path;
use std::sync::{Arc, Mutex};

use jsont::{
    DiagnosticEvent, DiagnosticSink, JsonTNamespace, JsonTValue, Parseable,
    SchemaRegistry, SinkError, ValidationPipeline, write_row,
};

// ── Paths (relative to workspace root, which is cwd during `cargo test`) ─────

const SCHEMA_PATH: &str = "../../../examples/healthcare/hl7/hl7_834.jsont";
const RUST_OUT:    &str = "../../cross-compat/hl7-rust-generated.jsont.data";
const JAVA_OUT:    &str = "../../cross-compat/hl7-java-generated.jsont.data";

// =============================================================================
// Capture sink
// =============================================================================

struct CaptureSink(Arc<Mutex<Vec<DiagnosticEvent>>>);

impl CaptureSink {
    fn new() -> (Self, Arc<Mutex<Vec<DiagnosticEvent>>>) {
        let buf = Arc::new(Mutex::new(Vec::new()));
        (Self(Arc::clone(&buf)), buf)
    }
}

impl DiagnosticSink for CaptureSink {
    fn emit(&mut self, event: DiagnosticEvent) {
        self.buf_mut().push(event);
    }
    fn flush(&mut self) -> Result<(), SinkError> { Ok(()) }
}

impl CaptureSink {
    fn buf_mut(&self) -> std::sync::MutexGuard<'_, Vec<DiagnosticEvent>> {
        self.0.lock().unwrap()
    }
}

// =============================================================================
// Row builders — must match Java Hl7834CrossCompatTest exactly
// =============================================================================

fn build_gs_rows() -> Vec<jsont::JsonTRow> {
    let senders   = ["SENDER-A",  "SENDER-B",  "PLAN-SPONSOR-1", "PLAN-SPONSOR-2", "CLEARINGHOUSE"];
    let receivers = ["RECEIVER-A","RECEIVER-B","PAYER-1",         "PAYER-2",         "INSURER-X"];
    let controls: [u32; 5] = [100_000_001, 200_000_002, 300_000_003, 400_000_004, 500_000_005];

    (0..5).map(|i| {
        jsont::JsonTRowBuilder::new()
            .push(JsonTValue::str("BE"))
            .push(JsonTValue::str(senders[i]))
            .push(JsonTValue::str(receivers[i]))
            .push(JsonTValue::str("2026-04-09"))  // date: groupDate
            .push(JsonTValue::str("10:30:00"))     // time: groupTime
            .push(JsonTValue::u32(controls[i]))
            .push(JsonTValue::str("X"))
            .push(JsonTValue::str("005010X220A1"))
            .build()
    }).collect()
}

fn build_ins_rows() -> Vec<jsont::JsonTRow> {
    // (memberIndicator, relationshipCode, maintenanceTypeCode, maintenanceReasonCode,
    //  benefitStatusCode, medicareStatusCode, consolidatedOmnibus, employmentStatusCode)
    let data: [(&str, &str, &str, Option<&str>, &str, &str, &str, &str); 5] = [
        ("Y", "SELF",     "ADDITION",    None,              "A", "1", "N", "FT"),
        ("N", "CHILD",    "CHANGE",      Some("BIRTH"),     "A", "1", "N", "FT"),
        ("N", "SPOUSE",   "CANCELLATION",Some("DIVORCE"),   "A", "1", "N", "FT"),
        ("N", "EMPLOYEE", "REINSTATEMENT",Some("ELIG_TERM"),"A", "1", "N", "FT"),
        ("Y", "SELF",     "AUDIT",       None,              "A", "1", "N", "FT"),
    ];

    (0..5).map(|i| {
        let (mem, rel, maint, reason, ben, med, cob, emp) = data[i];
        jsont::JsonTRowBuilder::new()
            .push(JsonTValue::str(mem))
            .push(JsonTValue::enum_val(rel))
            .push(JsonTValue::enum_val(maint))
            .push(match reason { Some(r) => JsonTValue::enum_val(r), None => JsonTValue::null() })
            .push(JsonTValue::str(ben))
            .push(JsonTValue::str(med))
            .push(JsonTValue::str(cob))
            .push(JsonTValue::str(emp))
            .build()
    }).collect()
}

// =============================================================================
// Schema + registry loader
// =============================================================================

fn load_registry() -> (JsonTNamespace, SchemaRegistry) {
    let src = fs::read_to_string(SCHEMA_PATH)
        .expect("hl7_834.jsont not found — run from workspace root");
    let ns = JsonTNamespace::parse(&src).expect("hl7_834.jsont failed to parse");
    let registry = SchemaRegistry::from_namespace(&ns).expect("registry build failed");
    (ns, registry)
}

// =============================================================================
// Tests
// =============================================================================

/// Parse hl7_834.jsont, generate 5 GS + 5 INS rows, write to the cross-compat file.
#[test]
fn hl7_cross_compat_generate() {
    let (ns, _registry) = load_registry();

    // Confirm the data-schema pointer resolves.
    assert_eq!(ns.data_schema, "Hl7_834", "data-schema must be Hl7_834");

    let mut rows = build_gs_rows();
    rows.extend(build_ins_rows());
    assert_eq!(rows.len(), 10);

    let path = Path::new(RUST_OUT);
    fs::create_dir_all(path.parent().unwrap()).expect("could not create cross-compat dir");
    let file = fs::File::create(path).expect("could not create hl7-rust-generated.jsont.data");
    let mut out = BufWriter::new(file);
    for (i, row) in rows.iter().enumerate() {
        if i > 0 { out.write_all(b",\n").unwrap(); }
        write_row(row, &mut out).expect("write_row failed");
    }
    out.flush().unwrap();

    let meta = fs::metadata(path).unwrap();
    println!("hl7-rust-generated.jsont.data: {} bytes, 10 rows", meta.len());
}

/// Read the Java-generated fixture, validate with both GS_Segment and INS_Segment pipelines.
#[test]
fn hl7_cross_compat_validate_java() {
    let content = fs::read_to_string(JAVA_OUT)
        .expect("hl7-java-generated.jsont.data not found — run Java Hl7834CrossCompatTest#generate_hl7_java first");

    let rows = Vec::<jsont::JsonTRow>::parse(&content)
        .expect("failed to parse hl7-java-generated.jsont.data");
    assert_eq!(rows.len(), 10, "expected 10 rows from Java fixture");

    let (_ns, registry) = load_registry();
    let gs_schema  = registry.get("GS_Segment") .expect("GS_Segment not found").clone();
    let ins_schema = registry.get("INS_Segment").expect("INS_Segment not found").clone();

    // ── Validate GS rows (0–4) ────────────────────────────────────────────────
    let (gs_sink, gs_buf) = CaptureSink::new();
    let gs_pipeline = ValidationPipeline::builder(gs_schema)
        .without_console()
        .with_sink(Box::new(gs_sink))
        .build()
        .expect("GS_Segment pipeline build failed");

    for i in 0..5 {
        gs_pipeline.validate_one(rows[i].clone(), |_| {});
    }
    let gs_events: Vec<DiagnosticEvent> = gs_buf.lock().unwrap()
        .iter().filter(|e| e.severity == jsont::Severity::Fatal).cloned().collect();
    assert!(gs_events.is_empty(), "GS rows must have zero fatals, got: {gs_events:?}");

    // ── Spot-check GS rows ────────────────────────────────────────────────────
    // Row 0: functionalCode=BE, senderCode=SENDER-A, controlNumber=100000001
    assert_str(&rows[0], 0, "BE");
    assert_str(&rows[0], 1, "SENDER-A");
    assert_str(&rows[0], 2, "RECEIVER-A");
    assert_numeric(&rows[0], 5, 100_000_001.0);

    // Row 2: plan-sponsor
    assert_str(&rows[2], 1, "PLAN-SPONSOR-1");
    assert_str(&rows[2], 2, "PAYER-1");
    assert_numeric(&rows[2], 5, 300_000_003.0);

    // Row 4: clearinghouse
    assert_str(&rows[4], 1, "CLEARINGHOUSE");

    // ── Validate INS rows (5–9) ───────────────────────────────────────────────
    let (ins_sink, ins_buf) = CaptureSink::new();
    let ins_pipeline = ValidationPipeline::builder(ins_schema)
        .without_console()
        .with_sink(Box::new(ins_sink))
        .build()
        .expect("INS_Segment pipeline build failed");

    for i in 5..10 {
        ins_pipeline.validate_one(rows[i].clone(), |_| {});
    }
    let ins_events: Vec<DiagnosticEvent> = ins_buf.lock().unwrap()
        .iter().filter(|e| e.severity == jsont::Severity::Fatal).cloned().collect();
    assert!(ins_events.is_empty(), "INS rows must have zero fatals, got: {ins_events:?}");

    // ── Spot-check INS rows ───────────────────────────────────────────────────
    // Row 5 (INS[0]): subscriber self, ADDITION, null reason
    assert_str (&rows[5], 0, "Y");
    assert_enum(&rows[5], 1, "SELF");
    assert_enum(&rows[5], 2, "ADDITION");
    assert_null(&rows[5], 3);

    // Row 6 (INS[1]): dependent child, CHANGE, BIRTH
    assert_str (&rows[6], 0, "N");
    assert_enum(&rows[6], 1, "CHILD");
    assert_enum(&rows[6], 2, "CHANGE");
    assert_enum(&rows[6], 3, "BIRTH");

    // Row 7 (INS[2]): spouse, CANCELLATION, DIVORCE
    assert_enum(&rows[7], 1, "SPOUSE");
    assert_enum(&rows[7], 2, "CANCELLATION");
    assert_enum(&rows[7], 3, "DIVORCE");

    // Row 9 (INS[4]): subscriber self, AUDIT, null reason
    assert_str (&rows[9], 0, "Y");
    assert_enum(&rows[9], 1, "SELF");
    assert_enum(&rows[9], 2, "AUDIT");
    assert_null(&rows[9], 3);

    println!("hl7_cross_compat_validate_java: all 10 rows valid (5 GS + 5 INS)");
}

// =============================================================================
// Assertion helpers
// =============================================================================

fn v(row: &jsont::JsonTRow, idx: usize) -> &JsonTValue {
    row.get(idx).unwrap_or_else(|| panic!("row has no field at index {idx}"))
}

fn assert_str(row: &jsont::JsonTRow, idx: usize, expected: &str) {
    match v(row, idx) {
        JsonTValue::Str(s) => assert_eq!(s.as_str(), expected, "field[{idx}] str mismatch"),
        other => panic!("field[{idx}] expected Str, got {other:?}"),
    }
}

fn assert_numeric(row: &jsont::JsonTRow, idx: usize, expected: f64) {
    match v(row, idx) {
        JsonTValue::Number(n) => {
            let actual = n.as_f64();
            assert!((actual - expected).abs() < 1.0,
                "field[{idx}] numeric mismatch: expected {expected}, got {actual}");
        }
        other => panic!("field[{idx}] expected Number, got {other:?}"),
    }
}

fn assert_enum(row: &jsont::JsonTRow, idx: usize, expected: &str) {
    match v(row, idx) {
        JsonTValue::Enum(e) => assert_eq!(e.as_str(), expected, "field[{idx}] enum mismatch"),
        other => panic!("field[{idx}] expected Enum, got {other:?}"),
    }
}

fn assert_null(row: &jsont::JsonTRow, idx: usize) {
    match v(row, idx) {
        JsonTValue::Null => {}
        other => panic!("field[{idx}] expected Null, got {other:?}"),
    }
}
