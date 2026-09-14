package com.irs.mef.newsend.journal;

import com.irs.mef.newsend.domain.Ein;
import com.irs.mef.newsend.domain.FormType;
import com.irs.mef.newsend.domain.NewSendFault;
import com.irs.mef.newsend.domain.NewSendOutcome;
import com.irs.mef.newsend.domain.NewSendSubmissionId;
import com.irs.mef.newsend.domain.NewSendSubmissionRecord;
import com.irs.mef.newsend.domain.TaxPeriod;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Write-ahead log for filings. The reason this port exists: an HTTP response is not durable,
 * and a lost deposit id turns into a duplicate filing. The in-memory implementation is
 * TEST-ONLY — it cannot satisfy the requirement the port was written for.
 */
public interface NewSendSubmissionJournal {

    /**
     * Atomically claim the record's clientRequestId and durably persist the CREATED row.
     * Must return only after the row survives process death (fsync for the file implementation).
     * A prior claim in ABANDONED state does not block — ABANDONED is the one key-releasing state.
     */
    NewSendReservation reserve(NewSendSubmissionRecord created);

    /** Apply a terminal wire outcome. Idempotent: re-applying the same outcome is a no-op. */
    void complete(NewSendSubmissionId submissionId, NewSendOutcome outcome, Instant at);

    /** Terminal ABANDONED — releases the key. Legal ONLY when nothing reached the wire. */
    void abandon(NewSendSubmissionId submissionId, NewSendFault fault, Instant at);

    Optional<NewSendSubmissionRecord> findBySubmissionId(NewSendSubmissionId submissionId);

    Optional<NewSendSubmissionRecord> findByClientRequestId(String clientRequestId);

    /**
     * Duplicate-period guard index: "has this client's quarter already gone out?" is a dominant
     * access pattern, present from day one. Callers filter by environment and state.
     */
    List<NewSendSubmissionRecord> findFiledFor(Ein clientEin, FormType formType, TaxPeriod taxPeriod);

    /** Reservation result. AlreadyClaimed carries the existing row so the caller can answer without a second read. */
    sealed interface NewSendReservation {
        record Reserved(NewSendSubmissionRecord record) implements NewSendReservation {
        }

        record AlreadyClaimed(NewSendSubmissionRecord existing) implements NewSendReservation {
        }
    }
}
