# Next Actions

## P0 — Adoption Critical

1. ~~JSON interoperability~~ ✅ Complete
2. Implement Enterprise-Grade Security at parse level, that is, assuming we have whitelisted characters for String, Number, Boolean, only the whitelisted characters are allowed in the input text.  If there's any other character in the input text, throw an error, and report the error message along with row number, column number, and the character that caused the error
3. Schema Level restrictions - that is, max fields per schema, max schemas/enums per catalog, max length for schema or field identifiers.  Max depth allowed in any schema or catalog or namespace.  System shall have default limits, but they can be configured via a configuration file.

---

## P1 — Security / Rust Parity

1. **Rust implementation: apply Java's design changes**

   The Java implementation was refactored for SOLID principles. The Rust implementation needs
   the same treatment before the two diverge further.

   ### Step 1 — Command pattern for operation applicator

   * Current: large `match` / `if-let` chain in the operation applicator
   * Target: one handler type per `SchemaOperation` variant — a trait `RowOperationHandler`
     with `supports` + `apply`, and a `FieldResolutionHandler` trait for field-name shape changes
   * Registry: `OperationApplicator` holds a `Vec<Box<dyn RowOperationHandler>>` and dispatches

   ### Step 2 — Scope validators for derived-schema validation

   * Current: inline `match` in `validateWithParent` / equivalent Rust function
   * Target: `OperationScopeValidator` trait, one struct per operation type, orchestrated by `ScopeValidation::validate`

   ### ~~Step 3 — ASCON-128a field cipher + ECDH DEK wrapping (Rust)~~ ✅ Complete

   * Implemented via `ascon-aead` crate (RustCrypto family)
   * All 3 algorithms (AES-GCM, ChaCha20-Poly1305, ASCON-128a) × 2 KEK modes (RSA, ECDH) working

   ### ~~Step 4 — EcdhCryptoConfig `ofKeys` equivalent (Rust)~~ ✅ Complete

   * `EcdhCryptoConfig::of_keys(peer_pub_der: Vec<u8>, host_priv_pem: &str)` added
   * Enables in-process testing without environment setup

   ### ~~Step 5 — Wire compatibility tests~~ ✅ Complete

   * Crypto layer: `code/cross-compat/fixtures/` — 4 committed fixture files (RSA + ECDH × Rust + Java)
   * Full stream: `code/cross-compat/rust-encrypted.jsont` + `java-encrypted.jsont` — 10 rows each, verified bidirectionally
   * `EncryptHeader` round-trip, per-field payload structure, SHA-256 digest verification — all confirmed

   ### Important: apply SOLID principles throughout

   The user confirmed that SOLID principles and appropriate design patterns are the standard
   for this codebase. When touching Rust code, apply the same command/strategy patterns
   as the Java refactor. Do not use large match blocks as the sole structure for extensible logic.

---

## P2 — Data Capabilities

1. **Nested field path resolution for all derived-schema operations**

   ### Problem
   The grammar allows dot-separated field paths (`address.city`, `member.name.first`) in all
   derived-schema operations. Both implementations silently fail to resolve them — each in a
   different, inconsistent way:

   | Operation | Rust (`path.join()`) | Java (`path.leaf()`) |
   |---|---|---|
   | `project(a.b)` | looks up key `"a.b"` → `FieldNotFound` | looks up key `"b"` → may accidentally match a top-level field named `"b"` |
   | `exclude(a.b)` | looks up key `"a.b"` → `FieldNotFound` | looks up key `"b"` → same accidental match risk |
   | `rename(a.b as c)` | looks up key `"a.b"` → `FieldNotFound` | looks up key `"b"` → accidental match |
   | `filter a.b > 0` | `EvalContext` has no key `"a.b"` → `UnboundField` | `FieldRefCollector` collects `leaf()="b"`, EvalContext has no `"b"` → `UnboundField` |
   | `transform c = a.b * 2` | looks up `"a.b"` in EvalContext → `UnboundField` | looks up `"b"` in EvalContext → `UnboundField` |
   | `decrypt(a.b)` | looks up field name `"a.b"` → `FieldNotFound` | field names are plain `String`, looks up `"a.b"` → `FieldNotFound` |

   **Root cause:** The working row in both implementations is a flat structure
   (`Vec<(String, JsonTValue)>` in Rust, `LinkedHashMap<String, JsonTValue>` in Java)
   keyed by the parent schema's own top-level field names only. No operation descends into
   `JsonTValue::Object` / `JsonTValue.Object` values to extract nested fields.

   **Java diverges from Rust:** Java uses `path.leaf()` throughout — the last segment only —
   while Rust uses `path.join()` — the full dot-joined string. Both are wrong for nested paths
   but in different ways: Java's approach can silently produce incorrect results if a top-level
   field happens to share the leaf name.

   ### Required changes (both implementations)

   **Step 1 — Flatten-on-entry (simpler, immediate)**
   When entering `apply_operations`, if the working row contains `Object`-typed values,
   optionally flatten them depth-first into the working map using dot-joined keys:
   `("address", Object([("city", "NYC"), ("zip", "10001")]))` →
   `("address.city", "NYC"), ("address.zip", "10001")`.
   All operations then work uniformly on the flat map. Re-nest on exit if needed.
   Risk: information loss if two nested schemas have identically named leaf fields at different
   paths — requires a collision policy.

   **Step 2 — Path-aware lookup (correct, more complex)**
   Change all handlers to resolve dot paths by descending into the `Object` value tree:
   `resolve(working, ["address", "city"])` → get `working["address"]` → get `.city` from
   that Object. Each handler needs a `get_nested(path)` / `set_nested(path, value)` helper.
   This is the correct design — paths retain their full semantics, no collision risk, no
   re-nesting pass required.

   **Step 3 — Grammar validation at parse/build time**
   Once runtime handles paths, the schema builder/parser must also validate that dot paths
   reference fields that actually exist in the parent schema at their declared depth.
   Currently `DecryptScopeValidator` (Rust) explicitly defers this check (`// deferred`).
   All scope validators need path-aware field resolution.

   ### Consistency fix needed now (before full implementation)
   Java must switch all `path.leaf()` calls to `path.dotJoined()` to match Rust's `path.join()`
   behaviour and eliminate the silent-wrong-match risk. This is a one-line change per handler
   and makes both implementations fail consistently (`FieldNotFound`) on nested paths
   rather than Java accidentally succeeding with the wrong field.

2. JOIN operations across schemas

---

## P3 — Platform Evolution

1. Runtime engine design
2. Playground / developer experience

---

## Notes

* Prioritize adoption over new abstractions
* Avoid moving to runtime before core adoption stabilizes
* jsont-crypto wire format is frozen by design — changes require a new `format_ver` bit
* ECDH host public key: the current ECDH implementation is mathematically correct — the host
  public key is implicit in the private key. Including peer public key bytes in HKDF info
  is a future security enhancement for stronger key binding, not a correctness fix.
