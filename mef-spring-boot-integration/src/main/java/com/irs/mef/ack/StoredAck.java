package com.irs.mef.ack;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record StoredAck(
        String ackId,
        String submissionId,
        Instant retrievedAt,
        AckSource source,
        String ackType,
        String timestamp,
        String ackFilePath,
        List<String> errorCodes,
        List<String> errorMessages,
        String details,
        String efin,
        String ein,
        String tin,
        Long taxableIncomeAmt,
        Long totalTaxAmt,
        Long balanceDueAmt,
        Long expectedRefundAmt,
        Long netIncomeLossAmt,
        String taxYear,
        String submissionType,
        String submissionCategoryCode,
        String electronicPostmarkTs,
        String irsReceivedDate,
        String taxPeriodEndDate,
        String irsSubmissionId,
        String receiptId,
        String paymentRequestCode,
        Boolean hasValidationErrors,
        Boolean hasValidationAlerts) {

    public StoredAck {
        if (submissionId == null || submissionId.isBlank()) {
            throw new IllegalArgumentException("stored ack requires a submission id");
        }
        Objects.requireNonNull(retrievedAt, "retrievedAt");
        Objects.requireNonNull(source, "source");
        errorCodes = errorCodes == null ? List.of() : List.copyOf(errorCodes);
        errorMessages = errorMessages == null ? List.of() : List.copyOf(errorMessages);
    }
}
