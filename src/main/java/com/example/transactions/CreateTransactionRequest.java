package com.example.transactions;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Currency;

public record CreateTransactionRequest(
        @NotBlank @Size(max = 255) String roundId,
        @NotBlank @Size(max = 255) String playerId,
        @NotBlank @Size(max = 255) String gameName,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 17, fraction = 2) BigDecimal betAmount,
        @NotNull @Pattern(regexp = "[A-Z]{3}") String currency) {

    public CreateTransactionRequest {
        betAmount = Amounts.normalize(betAmount);
    }

    @AssertTrue(message = "currency must be a valid ISO 4217 code")
    public boolean isValidCurrency() {
        if (currency == null) {
            return true;
        }
        try {
            Currency.getInstance(currency);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
