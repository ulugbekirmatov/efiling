package com.irs.mef.filing;


import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface Journal {

    /**
     * Atomically claim the record's clientRequestId and durably persist the CREATED row.
     * Must return only after the row survives process death (fsync for the file implementation).
     * A prior claim in ABANDONED or REJECTED state does not block. Both release the key.
     */
    Reservation reserve(SubmissionRecord created);

    /** Apply a terminal wire outcome. Idempotent: re-applying the same outcome is a no-op. */
    void complete(SubmissionId submissionId, FilingOutcome outcome, Instant at);

    /** Terminal ABANDONED — releases the key. Legal ONLY when nothing reached the wire. */
    void abandon(SubmissionId submissionId, IrsFault fault, Instant at);

    Optional<SubmissionRecord> findBySubmissionId(SubmissionId submissionId);

    Optional<SubmissionRecord> findByClientRequestId(String clientRequestId);

    List<SubmissionRecord> findFiledFor(Ein clientEin, FormSpec formType, TaxPeriod taxPeriod);

    List<SubmissionRecord> findByClientId(String clientId);

    sealed interface Reservation {
        record Reserved(SubmissionRecord record) implements Reservation {
        }

        record AlreadyClaimed(SubmissionRecord existing) implements Reservation {
        }
    }
}
