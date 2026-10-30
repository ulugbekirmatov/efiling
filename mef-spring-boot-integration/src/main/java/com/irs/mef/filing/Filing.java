package com.irs.mef.filing;

import java.time.Instant;

public record Filing(
        SubmissionId submissionId,
        SubmitCommand command,
        Instant postmark) {
}
