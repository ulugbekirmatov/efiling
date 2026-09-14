package com.irs.mef.newsend;

import com.irs.mef.newsend.domain.NewSendOutcome;
import com.irs.mef.newsend.domain.NewSendSubmissionId;

/**
 * Service-level result: the wire outcome plus the two facts only the service knows —
 * whether this was an idempotent replay, and whether the journal write after the wire call
 * succeeded ({@code journalDurable=false} means the receipt is returned but was NOT persisted;
 * withholding a deposit id the caller cannot otherwise recover would be the worse failure).
 */
public record NewSendSubmitResult(
        NewSendSubmissionId submissionId,
        NewSendOutcome outcome,
        boolean replay,
        boolean journalDurable) {
}
