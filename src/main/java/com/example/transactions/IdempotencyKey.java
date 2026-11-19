package com.example.transactions;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** Claims an Idempotency-Key and keeps what is needed to compare and replay the original request. */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey extends AssignedIdEntity<String> {

    @Id
    private String idempotencyKey;
    private String roundId;
    private String playerId;
    private String gameName;
    private BigDecimal betAmount;
    private String currency;
    private UUID transactionId;
    private int responseStatus;
    private String responseBody;

    protected IdempotencyKey() {
    }

    IdempotencyKey(String key, CreateTransactionRequest request, UUID transactionId, int responseStatus,
            String responseBody) {
        this.idempotencyKey = key;
        this.roundId = request.roundId();
        this.playerId = request.playerId();
        this.gameName = request.gameName();
        this.betAmount = request.betAmount();
        this.currency = request.currency();
        this.transactionId = transactionId;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
    }

    boolean matches(CreateTransactionRequest request) {
        return roundId.equals(request.roundId())
                && playerId.equals(request.playerId())
                && gameName.equals(request.gameName())
                && betAmount.compareTo(request.betAmount()) == 0
                && currency.equals(request.currency());
    }

    @Override
    public String getId() {
        return idempotencyKey;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
