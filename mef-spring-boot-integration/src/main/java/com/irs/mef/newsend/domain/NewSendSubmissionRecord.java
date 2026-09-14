package com.irs.mef.newsend.domain;

import java.time.Instant;

/**
 * One journal row. Carries everything needed to reconcile with the IRS without the original
 * request — but never the return XML itself (wage PII); only its SHA-256 fingerprint.
 * Immutable: transitions produce a new record.
 */
public record NewSendSubmissionRecord(
        NewSendSubmissionId submissionId,
        String clientRequestId,
        String clientId,
        Ein clientEin,
        FormType formType,
        TaxPeriod taxPeriod,
        String returnXmlSha256,
        String environment,
        NewSendState state,
        Instant createdAt,
        Instant completedAt,
        NewSendReceipt receipt,
        NewSendFault fault) {

    public static NewSendSubmissionRecord created(NewSendFiling filing, String environment) {
        NewSendSubmitCommand command = filing.command();
        return new NewSendSubmissionRecord(
                filing.submissionId(),
                command.clientRequestId(),
                command.clientId(),
                command.clientEin(),
                command.formType(),
                command.taxPeriod(),
                command.returnXmlSha256(),
                environment,
                NewSendState.CREATED,
                filing.postmark(),
                null,
                null,
                null);
    }

    /** Apply a terminal wire outcome. Idempotent: re-applying the same terminal state is a no-op. */
    public NewSendSubmissionRecord completedWith(NewSendOutcome outcome, Instant at) {
        if (state != NewSendState.CREATED) {
            if (state == outcome.state()) {
                return this;
            }
            throw new IllegalStateException("Cannot move " + submissionId.value()
                    + " from terminal state " + state + " to " + outcome.state());
        }
        if (outcome instanceof NewSendOutcome.Transmitted transmitted) {
            return with(NewSendState.TRANSMITTED, at, transmitted.receipt(), null);
        }
        if (outcome instanceof NewSendOutcome.Rejected rejected) {
            return with(NewSendState.REJECTED, at, null, rejected.fault());
        }
        NewSendOutcome.Indeterminate indeterminate = (NewSendOutcome.Indeterminate) outcome;
        return with(NewSendState.INDETERMINATE, at, null, indeterminate.fault());
    }

    /** Terminal ABANDONED — releases the idempotency key. Legal ONLY when nothing reached the wire. */
    public NewSendSubmissionRecord abandoned(NewSendFault abandonFault, Instant at) {
        if (state != NewSendState.CREATED) {
            throw new IllegalStateException("Cannot abandon " + submissionId.value()
                    + " from terminal state " + state);
        }
        return with(NewSendState.ABANDONED, at, null, abandonFault);
    }

    public boolean releasesIdempotencyKey() {
        return state == NewSendState.ABANDONED;
    }

    private NewSendSubmissionRecord with(NewSendState nextState, Instant at,
                                         NewSendReceipt nextReceipt, NewSendFault nextFault) {
        return new NewSendSubmissionRecord(submissionId, clientRequestId, clientId, clientEin, formType,
                taxPeriod, returnXmlSha256, environment, nextState, createdAt, at, nextReceipt, nextFault);
    }
}
