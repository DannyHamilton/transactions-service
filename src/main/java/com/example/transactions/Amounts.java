package com.example.transactions;

import java.math.BigDecimal;

final class Amounts {

    private static final int SCALE = 2;
    private static final int MAX_INTEGER_DIGITS = 17; // NUMERIC(19,2)

    private Amounts() {
    }

    /**
     * Canonicalizes to two decimals so 10.5, 10.50 and 10.500 are the same value. Values that are
     * too precise or too large are returned untouched for Bean Validation to reject; scaling them
     * could allocate an enormous BigDecimal (e.g. 1e999999999).
     */
    static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() > SCALE || stripped.precision() - stripped.scale() > MAX_INTEGER_DIGITS) {
            return stripped;
        }
        return stripped.setScale(SCALE);
    }
}
