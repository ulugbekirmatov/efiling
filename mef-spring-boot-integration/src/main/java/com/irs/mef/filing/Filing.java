package com.irs.mef.filing;

import java.time.Instant;

/**
 * Everything the wire needs, and nothing else. Assembled by the service, consumed by the gateway.
 * The same Instant that produced the submission id is the postmark, so the id's yyyyDDD and the
 * postmark can never disagree.
 */
public record Filing(
        SubmissionId submissionId,
        SubmitCommand command,
        Instant postmark) {
}
