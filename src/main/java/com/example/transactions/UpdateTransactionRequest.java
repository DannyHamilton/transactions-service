package com.example.transactions;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UpdateTransactionRequest(
        @NotNull TransactionStatus status,
        @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal winAmount) {

    public UpdateTransactionRequest {
        winAmount = Amounts.normalize(winAmount);
    }

    @AssertTrue(message = "status must be COMPLETED or CANCELLED")
    public boolean isTargetStatus() {
        return status != TransactionStatus.PENDING;
    }

    @AssertTrue(message = "winAmount is required when COMPLETED and not allowed when CANCELLED")
    public boolean isWinAmountConsistent() {
        return switch (status == null ? TransactionStatus.PENDING : status) {
            case COMPLETED -> winAmount != null;
            case CANCELLED -> winAmount == null;
            case PENDING -> true;
        };
    }
}
