package com.example.transactions;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction extends AssignedIdEntity<UUID> {

    @Id
    private UUID id;
    private String roundId;
    private String playerId;
    private String gameName;
    private BigDecimal betAmount;
    private BigDecimal winAmount;
    private String currency;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    private Instant createdAt;

    protected Transaction() {
    }

    Transaction(UUID id, CreateTransactionRequest request, Instant createdAt) {
        this.id = id;
        this.roundId = request.roundId();
        this.playerId = request.playerId();
        this.gameName = request.gameName();
        this.betAmount = request.betAmount();
        this.currency = request.currency();
        this.status = TransactionStatus.PENDING;
        this.createdAt = createdAt;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getRoundId() {
        return roundId;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getGameName() {
        return gameName;
    }

    public BigDecimal getBetAmount() {
        return betAmount;
    }

    public BigDecimal getWinAmount() {
        return winAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
