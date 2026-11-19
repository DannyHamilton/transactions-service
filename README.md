# Transactions Service

A small Spring Boot REST service that records casino wagers and their outcomes (create → complete or cancel) and stays correct when clients retry or send conflicting requests concurrently.

> **Status:** all endpoints below are implemented and covered by tests. See [DECISIONS.md](DECISIONS.md) for design reasoning, known limitations and next steps.

## Requirements

- JDK 17 or newer
- Nothing else: Maven is provided by the wrapper (`./mvnw`) and H2 is embedded.

## Build, test, run

```bash
./mvnw verify            # compile and run all tests
./mvnw test              # tests only
./mvnw spring-boot:run   # start on http://localhost:8080
```

Tests use an in-memory H2 database (`src/test/resources/application.properties`) through the real JPA stack, with no mocked repositories. The application itself uses a file-backed H2 database at `./data/transactions` so data survives restarts. Delete `./data` to reset it. The file-backed database is for a single running instance only.

## API

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/transactions` | Create a `PENDING` transaction. Requires an `Idempotency-Key` header. |
| GET | `/transactions/{id}` | Retrieve one transaction. |
| GET | `/transactions?playerId=&status=` | List, optionally filtered. Ordered by `createdAt`, then `transactionId`. |
| PATCH | `/transactions/{id}` | Complete (`winAmount` required) or cancel (`winAmount` not allowed). |

Errors are returned as RFC 9457 `application/problem+json`.

### Example requests

```bash
# Create (retrying with the same Idempotency-Key and body returns the original response)
curl -i -X POST http://localhost:8080/transactions \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 6f1c2f0e-8a52-4d1e-9d0b-0c6f3f2a9a11' \
  -d '{"roundId":"round-1","playerId":"player-1","gameName":"Blackjack","betAmount":10.00,"currency":"USD"}'

# Retrieve
curl -i http://localhost:8080/transactions/<transactionId>

# List
curl -i 'http://localhost:8080/transactions?playerId=player-1&status=PENDING'

# Complete
curl -i -X PATCH http://localhost:8080/transactions/<transactionId> \
  -H 'Content-Type: application/json' \
  -d '{"status":"COMPLETED","winAmount":12.50}'

# Cancel
curl -i -X PATCH http://localhost:8080/transactions/<transactionId> \
  -H 'Content-Type: application/json' \
  -d '{"status":"CANCELLED"}'
```

## Stack

Java 17 target, Spring Boot 3.5, Spring Web, Spring Data JPA, Bean Validation, H2, [uuid-creator](https://github.com/f4b6a3/uuid-creator) (UUIDv7).