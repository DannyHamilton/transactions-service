package com.example.transactions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class TransactionService {

    // Losing a race on the key's primary key rolls the attempt back; the next attempt sees the winner's commit.
    private static final int MAX_CREATE_ATTEMPTS = 5;

    /** The exact response to send, stored so a replay is byte-identical to the original. */
    record StoredResponse(int status, String body, UUID transactionId) {
    }

    private final TransactionRepository transactions;
    private final IdempotencyKeyRepository keys;
    private final TransactionTemplate tx;
    private final ObjectMapper json;

    TransactionService(TransactionRepository transactions, IdempotencyKeyRepository keys, TransactionTemplate tx,
            ObjectMapper json) {
        this.transactions = transactions;
        this.keys = keys;
        this.tx = tx;
        this.json = json;
    }

    // Deliberately not @Transactional: the constraint violation must surface outside the failed transaction.
    StoredResponse create(String key, CreateTransactionRequest request) {
        for (int attempt = 1; ; attempt++) {
            try {
                return tx.execute(status -> createOrReplay(key, request));
            } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
                if (attempt == MAX_CREATE_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }

    private StoredResponse createOrReplay(String key, CreateTransactionRequest request) {
        Optional<IdempotencyKey> existing = keys.findById(key);
        if (existing.isPresent()) {
            IdempotencyKey stored = existing.get();
            if (!stored.matches(request)) {
                throw new ConflictException("Idempotency-Key was already used with a different request body");
            }
            return new StoredResponse(stored.getResponseStatus(), stored.getResponseBody(), stored.getTransactionId());
        }

        Transaction created = transactions.saveAndFlush(new Transaction(
                UuidCreator.getTimeOrderedEpoch(), request, Instant.now().truncatedTo(ChronoUnit.MICROS)));
        String body = render(TransactionResponse.from(created));
        keys.saveAndFlush(new IdempotencyKey(key, request, created.getId(), 201, body));
        return new StoredResponse(201, body, created.getId());
    }

    TransactionResponse update(UUID id, UpdateTransactionRequest request) {
        return tx.execute(status -> {
            int updated = transactions.settleIfPending(id, request.status(), request.winAmount(),
                    TransactionStatus.PENDING);
            Optional<Transaction> current = transactions.findById(id);
            if (updated == 0) {
                if (current.isEmpty()) {
                    throw new NotFoundException("Transaction " + id + " not found");
                }
                throw new ConflictException("Transaction " + id + " is " + current.get().getStatus()
                        + " and can no longer be changed");
            }
            return TransactionResponse.from(current.orElseThrow());
        });
    }

    TransactionResponse get(UUID id) {
        return transactions.findById(id)
                .map(TransactionResponse::from)
                .orElseThrow(() -> new NotFoundException("Transaction " + id + " not found"));
    }

    List<TransactionResponse> list(String playerId, TransactionStatus status) {
        Specification<Transaction> spec = (root, query, cb) -> cb.conjunction();
        if (playerId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("playerId"), playerId));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        return transactions.findAll(spec, Sort.by("createdAt", "id")).stream()
                .map(TransactionResponse::from)
                .toList();
    }

    private String render(TransactionResponse response) {
        try {
            return json.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
