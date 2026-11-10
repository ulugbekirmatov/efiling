package com.irs.mef.checkbook;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/** Body of {@code POST /check/digital}. {@code recipient} is an email address or a phone number. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DigitalCheckRequest(String name, String recipient, BigDecimal amount, String description,
                                  CheckAttachment attachment) {

    public DigitalCheckRequest {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("recipient must not be blank");
        }
        CheckAmount.requireValid(amount);
    }
}
