package com.irs.mef.filing;

import com.irs.mef.filing.Journal.Reservation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Offline. Pins the idempotency-key protocol: claim, replay lookup, and ABANDONED release. */
class InMemoryJournalTest {

    private final InMemoryJournal journal = new InMemoryJournal();

    private static SubmissionRecord created(String submissionId, String requestKey) {
        SubmitCommand command = new SubmitCommand(
                requestKey, "orchid", new Ein("003000004"), FormSpec.F941,
                new TaxPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)),
                "<Return xmlns=\"http://www.irs.gov/efile\"/>");
        Filing filing = new Filing(new SubmissionId(submissionId), command,
                Instant.parse("2026-03-31T12:00:00Z"));
        return SubmissionRecord.created(filing, "ATS");
    }

    @Test
    @DisplayName("a fresh key reserves; the same key is AlreadyClaimed with the original row")
    void reserveClaimsTheKey() {
        Reservation first = journal.reserve(created("2386892026090aaaa111", "key-1"));
        Reservation second = journal.reserve(created("2386892026090bbbb222", "key-1"));

        assertInstanceOf(Reservation.Reserved.class, first);
        Reservation.AlreadyClaimed claimed =
                assertInstanceOf(Reservation.AlreadyClaimed.class, second);
        assertEquals("2386892026090aaaa111", claimed.existing().submissionId().value());
    }

    @Test
    @DisplayName("ABANDONED releases the key; TRANSMITTED does not")
    void onlyAbandonedReleasesTheKey() {
        SubmissionRecord record = created("2386892026090aaaa111", "key-1");
        journal.reserve(record);
        journal.abandon(record.submissionId(), IrsFault.lockTimeout(), Instant.now());

        assertInstanceOf(Reservation.Reserved.class,
                journal.reserve(created("2386892026090cccc333", "key-1")));

        SubmissionRecord transmittedRecord = created("2386892026090dddd444", "key-2");
        journal.reserve(transmittedRecord);
        journal.complete(transmittedRecord.submissionId(), transmitted(transmittedRecord), Instant.now());

        assertInstanceOf(Reservation.AlreadyClaimed.class,
                journal.reserve(created("2386892026090eeee555", "key-2")));
    }

    @Test
    @DisplayName("complete records the receipt and the terminal state")
    void completeStoresTheOutcome() {
        SubmissionRecord record = created("2386892026090aaaa111", "key-1");
        journal.reserve(record);
        journal.complete(record.submissionId(), transmitted(record), Instant.parse("2026-03-31T12:01:00Z"));

        SubmissionRecord stored = journal.findBySubmissionId(record.submissionId()).orElseThrow();
        assertEquals(JournalState.TRANSMITTED, stored.state());
        assertEquals("DEP-1", stored.receipt().depositId());
        assertEquals(Instant.parse("2026-03-31T12:01:00Z"), stored.completedAt());
    }

    @Test
    @DisplayName("the duplicate-period index finds a prior filing for the same EIN, form, and period")
    void filingIdentityIndexAnswersThePeriodGuard() {
        SubmissionRecord record = created("2386892026090aaaa111", "key-1");
        journal.reserve(record);

        assertEquals(1, journal.findFiledFor(new Ein("003000004"), FormSpec.F941,
                new TaxPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31))).size());
        assertTrue(journal.findFiledFor(new Ein("003000004"), FormSpec.F940,
                new TaxPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31))).isEmpty());
    }

    private static FilingOutcome.Transmitted transmitted(SubmissionRecord record) {
        return new FilingOutcome.Transmitted(new TransmissionReceipt(
                "DEP-1", record.submissionId(), Instant.parse("2026-03-31T12:00:30Z"),
                TransmissionReceipt.TimestampSource.IRS, 1));
    }
}
