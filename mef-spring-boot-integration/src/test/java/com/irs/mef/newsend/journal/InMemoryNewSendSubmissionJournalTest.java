package com.irs.mef.newsend.journal;

import com.irs.mef.newsend.domain.Ein;
import com.irs.mef.newsend.domain.FormType;
import com.irs.mef.newsend.domain.NewSendFault;
import com.irs.mef.newsend.domain.NewSendFiling;
import com.irs.mef.newsend.domain.NewSendOutcome;
import com.irs.mef.newsend.domain.NewSendReceipt;
import com.irs.mef.newsend.domain.NewSendState;
import com.irs.mef.newsend.domain.NewSendSubmissionId;
import com.irs.mef.newsend.domain.NewSendSubmissionRecord;
import com.irs.mef.newsend.domain.NewSendSubmitCommand;
import com.irs.mef.newsend.domain.TaxPeriod;
import com.irs.mef.newsend.journal.NewSendSubmissionJournal.NewSendReservation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Offline. Pins the idempotency-key protocol: claim, replay lookup, and ABANDONED release. */
class InMemoryNewSendSubmissionJournalTest {

    private final InMemoryNewSendSubmissionJournal journal = new InMemoryNewSendSubmissionJournal();

    private static NewSendSubmissionRecord created(String submissionId, String requestKey) {
        NewSendSubmitCommand command = new NewSendSubmitCommand(
                requestKey, "orchid", new Ein("003000004"), FormType.F941,
                new TaxPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)),
                "<Return xmlns=\"http://www.irs.gov/efile\"/>", false);
        NewSendFiling filing = new NewSendFiling(new NewSendSubmissionId(submissionId), command,
                Instant.parse("2026-03-31T12:00:00Z"));
        return NewSendSubmissionRecord.created(filing, "ATS");
    }

    @Test
    @DisplayName("a fresh key reserves; the same key is AlreadyClaimed with the original row")
    void reserveClaimsTheKey() {
        NewSendReservation first = journal.reserve(created("2386892026090aaaa111", "key-1"));
        NewSendReservation second = journal.reserve(created("2386892026090bbbb222", "key-1"));

        assertInstanceOf(NewSendReservation.Reserved.class, first);
        NewSendReservation.AlreadyClaimed claimed =
                assertInstanceOf(NewSendReservation.AlreadyClaimed.class, second);
        assertEquals("2386892026090aaaa111", claimed.existing().submissionId().value());
    }

    @Test
    @DisplayName("ABANDONED releases the key; TRANSMITTED does not")
    void onlyAbandonedReleasesTheKey() {
        NewSendSubmissionRecord record = created("2386892026090aaaa111", "key-1");
        journal.reserve(record);
        journal.abandon(record.submissionId(), NewSendFault.lockTimeout(), Instant.now());

        assertInstanceOf(NewSendReservation.Reserved.class,
                journal.reserve(created("2386892026090cccc333", "key-1")));

        NewSendSubmissionRecord transmittedRecord = created("2386892026090dddd444", "key-2");
        journal.reserve(transmittedRecord);
        journal.complete(transmittedRecord.submissionId(), transmitted(transmittedRecord), Instant.now());

        assertInstanceOf(NewSendReservation.AlreadyClaimed.class,
                journal.reserve(created("2386892026090eeee555", "key-2")));
    }

    @Test
    @DisplayName("complete records the receipt and the terminal state")
    void completeStoresTheOutcome() {
        NewSendSubmissionRecord record = created("2386892026090aaaa111", "key-1");
        journal.reserve(record);
        journal.complete(record.submissionId(), transmitted(record), Instant.parse("2026-03-31T12:01:00Z"));

        NewSendSubmissionRecord stored = journal.findBySubmissionId(record.submissionId()).orElseThrow();
        assertEquals(NewSendState.TRANSMITTED, stored.state());
        assertEquals("DEP-1", stored.receipt().depositId());
        assertEquals(Instant.parse("2026-03-31T12:01:00Z"), stored.completedAt());
    }

    @Test
    @DisplayName("the duplicate-period index finds a prior filing for the same EIN, form, and period")
    void filingIdentityIndexAnswersThePeriodGuard() {
        NewSendSubmissionRecord record = created("2386892026090aaaa111", "key-1");
        journal.reserve(record);

        assertEquals(1, journal.findFiledFor(new Ein("003000004"), FormType.F941,
                new TaxPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31))).size());
        assertTrue(journal.findFiledFor(new Ein("003000004"), FormType.F940,
                new TaxPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31))).isEmpty());
    }

    private static NewSendOutcome.Transmitted transmitted(NewSendSubmissionRecord record) {
        return new NewSendOutcome.Transmitted(new NewSendReceipt(
                "DEP-1", record.submissionId(), Instant.parse("2026-03-31T12:00:30Z"),
                NewSendReceipt.TimestampSource.IRS, 1));
    }
}
