package com.irs.mef.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.irs.mef.filing.FilingReceipt;
import com.irs.mef.filing.FilingOutcome;
import com.irs.mef.filing.SubmitResult;
import com.irs.mef.filing.SubmissionRecord;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Successful-transmission response. Note what is ABSENT: no "accepted" flag and no
 * {@code ACCEPTED} state — SendSubmissions proves receipt, never acceptance. Acceptance
 * arrives via the acknowledgement services, typically 2-5 minutes later.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FilingResponse(
        String submissionId,
        String state,
        String depositId,
        Instant receiptTimestamp,
        String receiptTimestampSource,
        int receiptCount,
        String clientRequestId,
        String clientId,
        String clientEin,
        String formType,
        LocalDate taxPeriodBegin,
        LocalDate taxPeriodEnd,
        String environment,
        boolean replay,
        boolean journalDurable,
        String message) {

    private static final String RECEIVED_MESSAGE =
            "Received by IRS. A receipt confirms transmission only, NOT acceptance. "
                    + "Acceptance arrives via acknowledgements, typically 2-5 minutes later.";
    private static final String REPLAY_MESSAGE =
            "Replay of a previously transmitted filing. Nothing was re-sent to the IRS.";

    public static FilingResponse from(SubmissionRecord record, SubmitResult result) {
        FilingOutcome.Transmitted transmitted = (FilingOutcome.Transmitted) result.outcome();
        FilingReceipt receipt = transmitted.receipt();
        return new FilingResponse(
                record.submissionId().value(),
                record.state().name(),
                receipt.depositId(),
                receipt.receiptTimestamp(),
                receipt.receiptTimestampSource().name(),
                receipt.receiptCount(),
                record.clientRequestId(),
                record.clientId(),
                record.clientEin().value(),
                record.formType().code(),
                record.taxPeriod().begin(),
                record.taxPeriod().end(),
                record.environment(),
                result.replay(),
                result.journalDurable(),
                result.replay() ? REPLAY_MESSAGE : RECEIVED_MESSAGE);
    }
}
