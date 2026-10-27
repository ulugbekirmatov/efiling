package com.irs.mef.filing;


import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Write-ahead log for filings. The reason this port exists: an HTTP response is not durable,
 * and a lost deposit id turns into a duplicate filing. The in-memory implementation is
 * TEST-ONLY — it cannot satisfy the requirement the port was written for.
 */
public interface Journal {

    /**
     * Atomically claim the record's clientRequestId and durably persist the CREATED row.
     * Must return only after the row survives process death (fsync for the file implementation).
     * A prior claim in ABANDONED state does not block — ABANDONED is the one key-releasing state.
     */
    NewSendReservation reserve(SubmissionRecord created);

    /** Apply a terminal wire outcome. Idempotent: re-applying the same outcome is a no-op. */
    void complete(SubmissionId submissionId, FilingOutcome outcome, Instant at);

    /** Terminal ABANDONED — releases the key. Legal ONLY when nothing reached the wire. */
    void abandon(SubmissionId submissionId, IrsFault fault, Instant at);

    Optional<SubmissionRecord> findBySubmissionId(SubmissionId submissionId);

    Optional<SubmissionRecord> findByClientRequestId(String clientRequestId);

    /**
     * Duplicate-period guard index: "has this client's quarter already gone out?" is a dominant
     * access pattern, present from day one. Callers filter by environment and state.
     */
    List<SubmissionRecord> findFiledFor(Ein clientEin, FormSpec formType, TaxPeriod taxPeriod);

    /** Reservation result. AlreadyClaimed carries the existing row so the caller can answer without a second read. */
    sealed interface NewSendReservation {
        record Reserved(SubmissionRecord record) implements NewSendReservation {
        }

        record AlreadyClaimed(SubmissionRecord existing) implements NewSendReservation {
        }
    }
}
