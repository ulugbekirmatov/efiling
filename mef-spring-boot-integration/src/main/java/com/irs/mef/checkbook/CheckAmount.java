package com.irs.mef.checkbook;

import java.math.BigDecimal;

/** Amount rule shared by every Checkbook check request. */
final class CheckAmount {

    private static final BigDecimal MINIMUM_AMOUNT = new BigDecimal("0.01");
    private static final int MAX_CENT_SCALE = 2;

    private CheckAmount() {
    }

    static void requireValid(BigDecimal amount) {
        if (amount == null || amount.compareTo(MINIMUM_AMOUNT) < 0) {
            throw new IllegalArgumentException("amount must be at least " + MINIMUM_AMOUNT);
        }
        if (amount.stripTrailingZeros().scale() > MAX_CENT_SCALE) {
            throw new IllegalArgumentException("amount must have at most " + MAX_CENT_SCALE + " decimal places");
        }
    }
}
