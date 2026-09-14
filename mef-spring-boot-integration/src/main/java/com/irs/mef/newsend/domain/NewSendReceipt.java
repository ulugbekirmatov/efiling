package com.irs.mef.newsend.domain;

import java.time.Instant;

/**
 * Proof of receipt from SendSubmissions — never proof of acceptance.
 * The timestamp's provenance is not a detail: {@code LOCAL} means the SDK exposed no
 * receipt timestamp and the value is our own clock at parse time.
 */
public record NewSendReceipt(
        String depositId,
        NewSendSubmissionId submissionId,
        Instant receiptTimestamp,
        TimestampSource receiptTimestampSource,
        int receiptCount) {

    public enum TimestampSource { IRS, LOCAL }
}
