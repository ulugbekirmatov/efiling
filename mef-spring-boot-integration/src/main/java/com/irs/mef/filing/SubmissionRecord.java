package com.irs.mef.filing;

import java.time.Instant;

/**
 * One journal row. Carries everything needed to reconcile with the IRS without the original
 * request — but never the return XML itself (wage PII); only its SHA-256 fingerprint.
 * Immutable: transitions produce a new record.
 */
public record SubmissionRecord(
        SubmissionId submissionId,
        String clientRequestId,
        String clientId,
        Ein clientEin,
        FormSpec formType,
        TaxPeriod taxPeriod,
        String returnXmlSha256,
        String environment,
        JournalState state,
        Instant createdAt,
        Instant completedAt,
        TransmissionReceipt receipt,
        IrsFault fault) {

    public static SubmissionRecord created(Filing filing, String environment) {
        SubmitCommand command = filing.command();
        return new SubmissionRecord(
                filing.submissionId(),
                command.clientRequestId(),
                command.clientId(),
                command.clientEin(),
                command.formType(),
                command.taxPeriod(),
                command.returnXmlSha256(),
                environment,
                JournalState.CREATED,
                filing.postmark(),
                null,
                null,
                null);
    }

    /** Apply a terminal wire outcome. Idempotent: re-applying the same terminal state is a no-op. */
    SubmissionRecord completedWith(FilingOutcome outcome, Instant at) {
        if (state != JournalState.CREATED) {
            if (state == outcome.state()) {
                return this;
            }
            throw new IllegalStateException("Cannot move " + submissionId.value()
                    + " from terminal state " + state + " to " + outcome.state());
        }
        if (outcome instanceof FilingOutcome.Transmitted transmitted) {
            return with(JournalState.TRANSMITTED, at, transmitted.receipt(), null);
        }
        if (outcome instanceof FilingOutcome.Rejected rejected) {
            return with(JournalState.REJECTED, at, null, rejected.fault());
        }
        FilingOutcome.Indeterminate indeterminate = (FilingOutcome.Indeterminate) outcome;
        return with(JournalState.INDETERMINATE, at, null, indeterminate.fault());
    }

    /** Terminal ABANDONED — releases the content key. Legal ONLY when nothing reached the wire. */
    SubmissionRecord abandoned(IrsFault abandonFault, Instant at) {
        if (state != JournalState.CREATED) {
            throw new IllegalStateException("Cannot abandon " + submissionId.value()
                    + " from terminal state " + state);
        }
        return with(JournalState.ABANDONED, at, null, abandonFault);
    }

    /**
     * ABANDONED (nothing reached the wire) and REJECTED (SOAP fault; the IRS filed nothing)
     * both release the content key. TRANSMITTED and INDETERMINATE keep it.
     */
    boolean releasesIdempotencyKey() {
        return state == JournalState.ABANDONED || state == JournalState.REJECTED;
    }

    private SubmissionRecord with(JournalState nextState, Instant at,
                                         TransmissionReceipt nextReceipt, IrsFault nextFault) {
        return new SubmissionRecord(submissionId, clientRequestId, clientId, clientEin, formType,
                taxPeriod, returnXmlSha256, environment, nextState, createdAt, at, nextReceipt, nextFault);
    }
}
