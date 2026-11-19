# Decisions

## Design decisions

### 1. Idempotency strategy
- `Idempotency-Key` is **globally unique** across creation requests, not scoped per player (confirmed with the assessment contact, Trace).
- Keys are retained **indefinitely**; the requirement is ≥ 24 h and expiry is not required. Trade-off: unbounded growth of the key table; a TTL cleanup job is the next step.
- Same key and equivalent body → the **original response** (201, stored body) is replayed, whether the requests are sequential or concurrent. Elapsed time is deliberately irrelevant.
- Same key and different body → **409 Conflict**; nothing is processed. A guessed key therefore reveals only that it exists, never the original response.
- The key table stores the key (unique, max 255 characters), the normalized request fields, the transaction ID and the original response status and JSON body. The response is replayed from this record, not rebuilt from the transaction, which may since have been completed or cancelled.
- Concurrency is enforced by a **database unique constraint**. The key claim and the transaction insert commit in one database transaction. The loser handles the constraint violation outside that transaction, then loads the key row: same body → replay; different body → 409; row absent (winner rolled back) → retry creation. A unique constraint alone does not replay anything.
- Body equivalence compares **normalized fields directly** instead of hashing canonical JSON, which avoids canonicalization of property order and whitespace. Amounts compare numerically, so `10.5` equals `10.50`.
- A 400 does not consume the key. If the commit succeeds but the response is lost, the client's retry replays the stored response.

### 2. Concurrency control for PATCH
Exactly one concurrent PATCH wins; the loser gets 409. Chosen: conditional update `UPDATE … WHERE id = ? AND status = 'PENDING'`; a row count of 0 means 409 (or 404 if the row does not exist). Chosen over optimistic locking (`@Version`) because it is one atomic statement that encodes the lifecycle rule ("PENDING may change once") directly.

### 3. Error model
RFC 9457 `application/problem+json` for all errors. It supersedes RFC 7807, which the assessment mentions. Spring's `spring.mvc.problemdetails.enabled=true` only covers Spring MVC's own exceptions, so domain errors need a `@RestControllerAdvice`.

### Other decisions
- **Transaction IDs:** UUIDv7 (time-ordered, RFC 9562); Java 17 has no generator, so we use `uuid-creator` 6.1.1. Chosen over sequential integers because IDs are not enumerable and can be generated without coordination across instances, and over UUIDv4 for index locality and a natural tie-break after `createdAt`. Trade-offs: a UUID is not a security control, and v7 reveals creation time.
- **Amounts:** numerically compared; after `stripTrailingZeros`, scale > 2 → 400 (never rounded), so `10.500` is accepted. Column is `NUMERIC(19,2)`; overflow → 400. Responses always render two decimals.
- **Strings:** `roundId`, `playerId`, `gameName` are `@NotBlank`, max 255. `currency` is a three-letter uppercase valid ISO 4217 code (`java.util.Currency`); `usd` → 400.
- **Request rules:** missing or blank `Idempotency-Key` → 400. PATCH precedence: body validation 400, then unknown ID 404, then non-PENDING 409. `status: PENDING`, negative `winAmount` → 400; explicit `"winAmount": null` on cancel is accepted. Non-UUID `{id}` and invalid `status` filter → 400.
- **Responses:** POST returns 201 with a `Location` header.
- **List ordering:** `createdAt`, then transaction ID. Confirmed with the assessment contact.
- **Persistence:** H2. Application uses a **file-backed** database, tests use **in-memory**. H2 automatic mixed mode (`AUTO_SERVER=TRUE`) was considered and **not** used: it only helps multiple processes sharing one file, and shared-file locking is fragile on network filesystems. File-backed storage may be dropped if time is short.
- **Schema:** explicit `schema.sql` (`CREATE TABLE IF NOT EXISTS`, `spring.sql.init.mode=always`) with `spring.jpa.hibernate.ddl-auto=none`, so the unique key constraint is visible and the file-backed database survives restarts.
- **Package:** `com.example.transactions` kept.
- **Out of scope:** authentication, authorization, pagination, logging, tracing, metrics, health endpoints, Docker/deployment.
- **Build:** Maven with wrapper. Spring Boot 3.5.16 (supports Java 17). Compiles for Java 17; all 84 tests pass on JDK 17.0.21 (Temurin) and JDK 21.
- **Tests:** written first and run to confirm they failed (79 of 84), then the implementation was added until all passed. Black-box HTTP against the real server and H2 (`@SpringBootTest(webEnvironment = RANDOM_PORT)`), using `java.net.http.HttpClient`; concurrency tests use an `ExecutorService` and a start latch.

## AI suggestions

| Suggestion | Outcome | Reason |
| --- | --- | --- |
| UUIDv4 / sequential IDs | Rejected in favor of UUIDv7 | Index locality and tie-break ordering; see Other decisions. |
| SHA-256 fingerprint of canonicalized request | Modified | Needs JSON canonicalization and numeric normalization; direct field comparison is simpler for this small body. |
| RFC 7807 problem+json | Modified | RFC 9457 supersedes it. |
| Optimistic locking for PATCH | Rejected | Conditional update chosen; see decision 2. |
| `uuid-creator` vs `java-uuid-generator` | Accepted `uuid-creator` | Both generate UUIDv7 and support Java 17; no practical benefit from switching. |
| Spring Boot 3.5.16; file-backed H2 without `AUTO_SERVER` | Accepted | Scaffold choices. |
| Replay storage, POST race handling, string rules, `Location` header and two-decimal amounts, `schema.sql` | Accepted | Proposed during planning; see decisions above. |
| Black-box tests via `java.net.http.HttpClient` | Accepted | `HttpURLConnection` (the default behind `TestRestTemplate`) cannot send PATCH. |
| Spring Data `saveAndFlush` for key and transaction inserts | **Wrong; fixed** | With an application-assigned ID, Spring Data calls `merge()`, so a losing concurrent POST UPDATED the winner's key row instead of failing on the duplicate. The concurrency test caught it: 2–5 transactions existed for one key. Fixed with `Persistable` (`AssignedIdEntity`) so inserts use `persist()`. |

## Known limitations / next steps
- Multi-instance behavior: file-backed H2 is single-instance only; a shared server database (e.g. PostgreSQL) would be required, and the unique-constraint and conditional-update approaches would carry over.
- Idempotency key expiry job; auth, observability, deployment.