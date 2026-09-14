package com.irs.mef.newsend.domain;

import java.time.Instant;

/**
 * Everything the wire needs, and nothing else. Assembled by the service, consumed by the gateway.
 * The same Instant that produced the submission id is the postmark, so the id's yyyyDDD and the
 * postmark can never disagree.
 */
public record NewSendFiling(
        NewSendSubmissionId submissionId,
        NewSendSubmitCommand command,
        Instant postmark) {

    /** Derived from the submission id — the single source of truth for the originator EFIN. */
    public Efin efin() {
        return submissionId.efin();
    }
}
