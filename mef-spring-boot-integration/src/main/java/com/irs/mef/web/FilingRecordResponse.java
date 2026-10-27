package com.irs.mef.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.irs.mef.filing.FilingReceipt;
import com.irs.mef.filing.SubmissionRecord;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One journal row on the wire — the shape of both {@code GET} endpoints and the base of the
 * submit response. Explicitly NOT live IRS status; it is what THIS system recorded.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FilingRecordResponse(
        String submissionId,
        String clientRequestId,
        String clientId,
        String clientEin,
        String formType,
        LocalDate taxPeriodBegin,
        LocalDate taxPeriodEnd,
        String returnXmlSha256,
        String environment,
        String state,
        Instant createdAt,
        Instant completedAt,
        String depositId,
        Instant receiptTimestamp,
        String receiptTimestampSource,
        String faultCode,
        String faultMessage,
        String logHint) {

    public static FilingRecordResponse from(SubmissionRecord record) {
        FilingReceipt receipt = record.receipt();
        return new FilingRecordResponse(
                record.submissionId().value(),
                record.clientRequestId(),
                record.clientId(),
                record.clientEin().value(),
                record.formType().code(),
                record.taxPeriod().begin(),
                record.taxPeriod().end(),
                record.returnXmlSha256(),
                record.environment(),
                record.state().name(),
                record.createdAt(),
                record.completedAt(),
                receipt != null ? receipt.depositId() : null,
                receipt != null ? receipt.receiptTimestamp() : null,
                receipt != null ? receipt.receiptTimestampSource().name() : null,
                record.fault() != null ? record.fault().code() : null,
                record.fault() != null ? record.fault().message() : null,
                record.fault() != null ? record.fault().logHint() : null);
    }
}
