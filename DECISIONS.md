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

### 2. Concurrency control for PATCH — Proposed
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

## Open questions (resolve before implementing; delete this section before submitting)

**Reasoning that is unclear or incomplete**
1. **UUIDv7 rationale.** The stated reasons are "UUID is a standard and safer than sequential integers", which also holds for UUIDv4. What is the specific reason for v7? (Candidates: time-ordered index locality, natural tie-break ordering for `createdAt`.) Note v7 exposes creation time in the ID.
2. **Idempotency-Key scope and privacy.** `playerId` is part of the compared body, so a different player reusing a key gets 409 and never sees the original response. Only an identical body (same player) is replayed. Is that sufficient, or does a globally shared key namespace need more (e.g. players guessing each other's keys to probe for existence)?
3. **Optimistic locking vs conditional update.** Which one, and why? See decision 2.
4. **Why indefinite retention over a 24 h+ TTL?** Speed is the stated reason; we should state the storage trade-off honestly in the final doc.

**Decisions not yet made**
5. **Numeric equivalence for body comparison.** Is `10.5` equal to `10.50`? Is `10.500` rejected (scale 3) or accepted because it equals 10.5? Proposal: compare with `compareTo` after rejecting values whose `stripTrailingZeros` scale exceeds 2.
6. **String normalization.** Is `currency` case-insensitive (normalize to upper case or reject `usd`)? Are strings trimmed? Are `roundId`/`playerId`/`gameName` length-limited or required non-blank?
7. **Replay status code.** Does a replayed POST return 201 (identical to original) or 200? Proposal: identical response, 201.
8. **Failed first attempts.** Does a 400 response consume the key? Proposal: no, only successful creations are stored. And if the concurrent winner rolls back, does the loser proceed to create?
9. **Missing/blank/oversized `Idempotency-Key`** and maximum length. Proposal: 400 if missing or blank, cap at 255 characters.
10. **PATCH error precedence.** For a non-PENDING transaction with an invalid body, is it 400 or 409? Unknown ID is 404. Proposal: validate body first (400), then 404, then state (409).
11. **PATCH edge cases.** `status: PENDING` → 400? Cancel with `"winAmount": null` explicitly present → accepted or 400? Negative `winAmount` → 400.
12. **Malformed IDs/filters.** Non-UUID `{id}`: 400 or 404? Invalid `status` filter: 400?
13. **Amount range.** Maximum magnitude / column precision (e.g. `NUMERIC(19,2)`); overflow → 400.
14. **Schema management.** `ddl-auto=update` is a stopgap for the file database; prefer explicit `schema.sql` or Flyway? Proposal: `schema.sql`, to keep the unique constraints visible.
15. **Package/group naming.** Currently `com.example.transactions`; change if you prefer another.
16. **UUID library.** `uuid-creator` was selected by the AI assistant; the alternative is `java-uuid-generator`. Confirm.
17. **Concurrent test technique.** Proposal: `@SpringBootTest(webEnvironment = RANDOM_PORT)` with real HTTP calls, an `ExecutorService` and a start latch, to exercise real transaction boundaries.

## AI suggestions

| Suggestion | Outcome | Reason |
| --- | --- | --- |
| UUIDv4 / sequential IDs | Rejected in favor of UUIDv7 | See open question 1. |
| SHA-256 fingerprint of canonicalized request | Modified (tentatively rejected) | Needs JSON canonicalization and numeric normalization; direct field comparison is simpler for a four-field-plus body. |
| RFC 7807 problem+json | Modified | RFC 9457 supersedes it. |
| Optimistic locking for PATCH | Pending | See decision 2. |
| `uuid-creator` library; Spring Boot 3.5.16; file-backed H2 without `AUTO_SERVER` | Accepted, pending confirmation | Scaffold choices; see open question 16. |

*To be completed with real outcomes as development proceeds, including any case where AI output was wrong and how it was caught.*

## Unfinished / next steps
- Entire API, idempotency, concurrency control and tests (scaffold only so far).
- Verify on a JDK 17 runtime.
- Multi-instance behavior: file-backed H2 is single-instance only; a shared server database (e.g. PostgreSQL) would be required, and the unique-constraint and conditional-update approaches would carry over.
- Idempotency key expiry job; auth, observability, deployment.