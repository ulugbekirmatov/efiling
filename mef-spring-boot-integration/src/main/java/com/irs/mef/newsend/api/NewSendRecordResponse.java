package com.irs.mef.newsend.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.irs.mef.newsend.domain.NewSendReceipt;
import com.irs.mef.newsend.domain.NewSendSubmissionRecord;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One journal row on the wire — the shape of both {@code GET} endpoints and the base of the
 * submit response. Explicitly NOT live IRS status; it is what THIS system recorded.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NewSendRecordResponse(
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

    public static NewSendRecordResponse from(NewSendSubmissionRecord record) {
        NewSendReceipt receipt = record.receipt();
        return new NewSendRecordResponse(
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
