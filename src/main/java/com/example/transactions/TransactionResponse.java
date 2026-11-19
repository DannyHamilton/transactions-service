package com.example.transactions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID transactionId,
        String roundId,
        String playerId,
        String gameName,
        BigDecimal betAmount,
        BigDecimal winAmount,
        String currency,
        TransactionStatus status,
        Instant createdAt) {

    static TransactionResponse from(Transaction t) {
        return new TransactionResponse(t.getId(), t.getRoundId(), t.getPlayerId(), t.getGameName(),
                t.getBetAmount(), t.getWinAmount(), t.getCurrency(), t.getStatus(), t.getCreatedAt());
    }
}
