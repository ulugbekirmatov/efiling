package com.irs.mef.checkbook;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Body of {@code POST /check/physical}. A null {@code fundingAccountId} debits the default bank account;
 * a wallet id debits that prefund wallet.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PhysicalCheckRequest(
        String name,
        MailingAddress recipient,
        BigDecimal amount,
        String description,
        @JsonProperty("account") String fundingAccountId,
        @JsonProperty("mail_type") MailType mailType,
        CheckAttachment attachment) {

    static final int MIN_NAME_LENGTH = 2;
    static final int MAX_NAME_LENGTH = 180;
    static final int MAX_ATTACHMENT_BYTES = 7 * 1024 * 1024;

    public PhysicalCheckRequest {
        if (name == null || name.length() < MIN_NAME_LENGTH || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "name must be " + MIN_NAME_LENGTH + " to " + MAX_NAME_LENGTH + " characters");
        }
        if (recipient == null) {
            throw new IllegalArgumentException("recipient must not be null");
        }
        CheckAmount.requireValid(amount);
        if (attachment != null && attachment.content().length > MAX_ATTACHMENT_BYTES) {
            throw new IllegalArgumentException("attachment must be at most " + MAX_ATTACHMENT_BYTES + " bytes");
        }
    }
}
