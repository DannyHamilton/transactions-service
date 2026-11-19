# Decisions

Status legend: **Decided** (made by the author or confirmed with the assessment contact), **Proposed** (suggested, awaiting confirmation).

## Design decisions

### 1. Idempotency strategy — Decided (mechanics Proposed)
- `Idempotency-Key` is **globally unique** across creation requests, not scoped per player (confirmed with the assessment contact, Trace).
- Keys are retained **indefinitely**; the requirement is ≥ 24 h and expiry is not required. Trade-off: unbounded growth of the key table. A TTL cleanup job would be the next step.
- Same key and equivalent body → the **original response** is replayed, whether the requests are sequential or concurrent. Elapsed time is deliberately irrelevant.
- Same key and different body → **409 Conflict**; nothing is processed.
- The original status and body are **stored** and replayed, not rebuilt from the transaction, which may since have been completed or cancelled.
- Concurrency is enforced by a **database unique constraint** on the key. A constraint alone does not replay anything, so the losing request must, after the winner commits, load the stored response (and handle the winner having rolled back).
- Body equivalence compares **normalized fields directly** instead of hashing canonical JSON. For this small request, field comparison avoids JSON canonicalization (property order, whitespace) altogether. The numeric rule still needs defining (see open questions).

### 2. Concurrency control for PATCH — Decided
Exactly one concurrent PATCH wins and the loser gets 409. Candidates: optimistic locking (`@Version`) or a conditional update (`UPDATE … WHERE id = ? AND status = 'PENDING'`, checking the updated row count). The conditional update is arguably simpler and more reliable here because the only invariant is "PENDING may change once", but it is not yet chosen.

### 3. Error model — Decided
RFC 9457 `application/problem+json` for all errors. It supersedes RFC 7807, which the assessment mentions. Spring's `spring.mvc.problemdetails.enabled=true` only covers Spring MVC's own exceptions, so domain errors need a `@RestControllerAdvice`.

### Other decisions
- **Transaction IDs:** UUIDv7 (time-ordered, standardized in RFC 9562). Java 17 has no generator, so we use `uuid-creator` 6.1.1. Chosen over UUIDv4 and sequential integers; the UUID standard is safer than sequential integers.
- **Amounts:** more than two decimal places → 400, never silently rounded.
- **List ordering:** `createdAt`, then transaction ID. Confirmed with the assessment contact.
- **Persistence:** H2. Application uses a **file-backed** database, tests use **in-memory**. H2 automatic mixed mode (`AUTO_SERVER=TRUE`) was considered and **not** used: it only helps multiple processes sharing one file, and shared-file locking is fragile on network filesystems. File-backed storage may be dropped if time is short.
- **Out of scope:** authentication, authorization, pagination, logging, tracing, metrics, health endpoints, Docker/deployment.
- **Build:** Maven with wrapper. Spring Boot 3.5.16 (supports Java 17). Compiles for Java 17; verified on JDK 21 (author) and JDK 27 (AI sandbox), so a JDK 17 run is still outstanding.

## Resolved decisions (formerly open questions)

- **UUIDv7 rationale:** time-ordered IDs give better index locality and a natural tie-break after `createdAt`. Trade-off: the ID reveals creation time.
- **Key scope/privacy:** a different body under an existing key returns 409 and only an identical body, including the same `playerId`, is replayed. Accepted despite no authentication; a guessed key reveals only that it exists.
- **PATCH concurrency:** conditional update `UPDATE … WHERE id=? AND status='PENDING'`; a row count of 0 means 409 (or 404 if the row is missing). Chosen over `@Version` because it is one atomic statement that encodes the lifecycle rule directly.
- **Key retention:** indefinite. Trade-off: the key table grows without bound. A TTL cleanup is listed as future work.
- **Amounts:** numerically compared, so `10.5` equals `10.50`. After `stripTrailingZeros`, scale > 2 returns 400, so `10.500` is accepted. Column is `NUMERIC(19,2)`; overflow returns 400.
- **Currency:** exactly three uppercase letters and a valid ISO 4217 code (`java.util.Currency`). `usd` returns 400.
- **Replay:** returns the stored status (201) and body.
- **Failed attempts:** a 400 does not consume the key. If the concurrent winner rolls back, the waiting loser retries creation.
- **Idempotency-Key header:** missing or blank returns 400; maximum 255 characters.
- **PATCH precedence:** body validation 400, then unknown ID 404, then non-PENDING state 409.
- **PATCH edge cases:** `status: PENDING` returns 400; negative `winAmount` returns 400; explicit `"winAmount": null` on cancel is accepted.
- **Malformed inputs:** non-UUID `{id}` returns 400; invalid `status` filter returns 400.
- **Schema:** explicit `schema.sql` with `spring.jpa.hibernate.ddl-auto=none`, so the unique key constraint is visible.
- **Package and UUID library:** `com.example.transactions` and `uuid-creator` 6.1.1 confirmed.
- **Concurrency tests:** `@SpringBootTest(webEnvironment = RANDOM_PORT)`, real HTTP, `ExecutorService` and a start latch.

## AI suggestions

| Suggestion | Outcome | Reason |
| --- | --- | --- |
| UUIDv4 / sequential IDs | Rejected in favor of UUIDv7 | See open question 1. |
| SHA-256 fingerprint of canonicalized request | Modified (tentatively rejected) | Needs JSON canonicalization and numeric normalization; direct field comparison is simpler for a four-field-plus body. |
| RFC 7807 problem+json | Modified | RFC 9457 supersedes it. |
| Optimistic locking for PATCH | Rejected (conditional update chosen) | See decision 2. |
| `uuid-creator` library; Spring Boot 3.5.16; file-backed H2 without `AUTO_SERVER` | Accepted, pending confirmation | Scaffold choices; see open question 16. |

*To be completed with real outcomes as development proceeds, including any case where AI output was wrong and how it was caught.*

## Unfinished / next steps
- Entire API, idempotency, concurrency control and tests (scaffold only so far).
- Verify on a JDK 17 runtime.
- Multi-instance behavior: file-backed H2 is single-instance only; a shared server database (e.g. PostgreSQL) would be required, and the unique-constraint and conditional-update approaches would carry over.
- Idempotency key expiry job; auth, observability, deployment.