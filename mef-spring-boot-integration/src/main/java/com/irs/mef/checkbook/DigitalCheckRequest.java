package com.irs.mef.checkbook;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/** Body of {@code POST /check/digital}. {@code recipient} is an email address or a phone number. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DigitalCheckRequest(String name, String recipient, BigDecimal amount, String description,
                                  CheckAttachment attachment) {

    private static final BigDecimal MINIMUM_AMOUNT = new BigDecimal("0.01");
    private static final int MAX_CENT_SCALE = 2;

    public DigitalCheckRequest {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("recipient must not be blank");
        }
        if (amount == null || amount.compareTo(MINIMUM_AMOUNT) < 0) {
            throw new IllegalArgumentException("amount must be at least " + MINIMUM_AMOUNT);
        }
        if (amount.stripTrailingZeros().scale() > MAX_CENT_SCALE) {
            throw new IllegalArgumentException("amount must have at most " + MAX_CENT_SCALE + " decimal places");
        }
    }
}
