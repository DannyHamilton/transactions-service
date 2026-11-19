# Known limitations and future enhancements

Shortcomings accepted for the assessment timebox, with what would address each. Design reasoning is in [DECISIONS.md](DECISIONS.md).

## Correctness and concurrency

| Limitation | Impact | Enhancement |
| --- | --- | --- |
| Idempotency keys are globally unique and there is no authentication. | A caller can claim another client's key first, causing that client's real request to return 409. | Add authentication and scope keys by principal (composite key of principal and `Idempotency-Key`). This revisits the global-scope decision confirmed with the assessment contact. |
| `create()` retries on any `DataIntegrityViolationException` or `ConcurrencyFailureException` (max 5 attempts). | A genuine integrity bug (for example a NOT NULL violation) is retried and ends as a 500 instead of failing fast. | Retry only when the violated constraint is the idempotency key's primary key. |
| The "winner rolls back, loser retries creation" path has no test. | It is hard to trigger deterministically, so it is covered by reasoning only. | Inject a failure after the key insert (test-only hook or `@MockitoSpyBean`) to exercise it. |
| Concurrency tests are probabilistic (12 callers, 5 to 10 repetitions each). | They can miss a rare race. | Add a mutation check in CI (remove the guard, expect failure) and raise repetitions in a scheduled job. |
| PATCH relies on READ COMMITTED semantics. | Under REPEATABLE READ or SERIALIZABLE (for example on PostgreSQL) the loser may get a serialization failure instead of 0 updated rows. | Map `ConcurrencyFailureException` on PATCH to 409. |
| Tests run only on H2. | H2 and PostgreSQL differ in locking and error behavior. | Run the same suite against PostgreSQL with Testcontainers. |

## Scale and operations

| Limitation | Impact | Enhancement |
| --- | --- | --- |
| File-backed H2 supports a single instance. | Cannot run several instances behind a load balancer. | Shared PostgreSQL with Flyway migrations; the unique-constraint and conditional-update approaches carry over. `createdAt` could come from the database clock to avoid cross-instance skew. |
| Idempotency keys are kept indefinitely. | The key table grows without bound. | Store a creation timestamp and add a scheduled cleanup after a retention period of at least 24 h. A retry after expiry would create a new transaction. |
| `GET /transactions` has no pagination and the `status` filter is not indexed. | Large result sets are slow and large. | Keyset pagination on (`created_at`, `id`) and an index to match the filters. |
| Authentication, authorization, logging, tracing, metrics and health endpoints are not implemented (out of scope per the assessment). | No access control or observability. | Spring Security with player-scoped access, Micrometer metrics, structured logs with trace IDs, Actuator health, container and CI/CD setup. |

## API behavior

| Limitation | Impact | Enhancement |
| --- | --- | --- |
| Validation failures return only `"detail": "Validation failure"`. | Clients cannot see which field failed. | Add per-field errors (an `errors` array) to the problem response. |
| String fields are not trimmed or normalized. | `" a"` and `"a"` are different players and different request bodies. | Trim on input and decide on case rules per field. |
| Amounts assume two decimal places for every currency, as the assessment allows. | Currencies with other minor units (for example JPY, KWD) are not modeled. | Validate scale per currency using `Currency.getDefaultFractionDigits()`. |
| `winAmount` is not related to `betAmount`. | No upper bound or business-rule check on payouts. | Add payout limits if the domain requires them. |
| Error responses use `type: about:blank`. | Clients cannot branch on a stable error type. | Define problem `type` URIs per error. |
