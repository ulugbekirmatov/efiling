package com.irs.mef.filing;

/**
 * What {@link FilingDesk#file} returns. {@code replay} is true when these bytes were already
 * journaled and nothing was sent to the IRS again.
 */
public record FilingReceipt(
        SubmissionId id,
        ClientId clientId,
        FilingOutcome outcome,
        boolean journalDurable,
        boolean replay) {
}
