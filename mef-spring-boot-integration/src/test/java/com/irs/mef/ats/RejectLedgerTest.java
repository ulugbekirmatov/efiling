package com.irs.mef.ats;

import com.irs.mef.ack.AckSource;
import com.irs.mef.ack.AckStore;
import com.irs.mef.ack.StoredAck;
import com.irs.mef.filing.Ein;
import com.irs.mef.filing.Filing;
import com.irs.mef.filing.FormSpec;
import com.irs.mef.filing.InMemoryJournal;
import com.irs.mef.filing.SubmissionId;
import com.irs.mef.filing.SubmissionRecord;
import com.irs.mef.filing.SubmitCommand;
import com.irs.mef.filing.TaxPeriod;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RejectLedgerTest {

    private static final String CLIENT = "ats-scenario-1-orchid-941";

    @Test
    void blocksOnAStoredRejectedAckWithAnUnreproducedCode() {
        InMemoryJournal journal = new InMemoryJournal();
        MemoryAcks acks = new MemoryAcks();
        SubmissionRecord older = row(CLIENT, "0000002026286aaaaaa1", Instant.parse("2026-10-01T00:00:00Z"));
        SubmissionRecord latest = row(CLIENT, "0000002026286bbbbbb2", Instant.parse("2026-10-14T00:00:00Z"));
        journal.reserve(older);
        journal.reserve(latest);
        acks.record(ack(older.submissionId().value(), "Rejected", List.of("R9999-000")));
        acks.record(ack(latest.submissionId().value(), "Rejected", List.of("R9999-000")));

        Optional<RejectLedger.Blocked> blocked = new RejectLedger(journal, acks).blockFor(AtsScenario.byId("1"));

        assertTrue(blocked.isPresent());
        assertEquals(latest.submissionId(), blocked.get().lastSend());
        assertEquals(Set.of("R9999-000"), blocked.get().unreproducedCodes());
    }

    @Test
    void clearsWhenTheCodeHasARule() {
        InMemoryJournal journal = new InMemoryJournal();
        MemoryAcks acks = new MemoryAcks();
        SubmissionRecord latest = row(CLIENT, "0000002026286bbbbbb2", Instant.parse("2026-10-14T00:00:00Z"));
        journal.reserve(latest);
        acks.record(ack(latest.submissionId().value(), "Rejected", List.of("X0000-008")));

        assertTrue(new RejectLedger(journal, acks).blockFor(AtsScenario.byId("1")).isEmpty());
    }

    @Test
    void anAcceptedLatestAckDoesNotBlock() {
        InMemoryJournal journal = new InMemoryJournal();
        MemoryAcks acks = new MemoryAcks();
        SubmissionRecord older = row(CLIENT, "0000002026286aaaaaa1", Instant.parse("2026-10-01T00:00:00Z"));
        SubmissionRecord latest = row(CLIENT, "0000002026286bbbbbb2", Instant.parse("2026-10-14T00:00:00Z"));
        journal.reserve(older);
        journal.reserve(latest);
        acks.record(ack(older.submissionId().value(), "Rejected", List.of("R9999-000")));
        acks.record(ack(latest.submissionId().value(), "Accepted", List.of()));

        assertTrue(new RejectLedger(journal, acks).blockFor(AtsScenario.byId("1")).isEmpty());
    }

    private static SubmissionRecord row(String clientId, String submissionId, Instant at) {
        SubmitCommand command = new SubmitCommand(
                submissionId, clientId, new Ein("003000004"), FormSpec.F941,
                new TaxPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)),
                "<Return/>");
        return SubmissionRecord.created(new Filing(new SubmissionId(submissionId), command, at), "ATS");
    }

    private static StoredAck ack(String submissionId, String ackType, List<String> codes) {
        return new StoredAck(
                null, submissionId, Instant.parse("2026-10-14T01:00:00Z"), AckSource.GET_ACK, ackType,
                null, null, codes, List.of(), null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, !codes.isEmpty(), false);
    }

    private static final class MemoryAcks implements AckStore {
        private final Map<String, StoredAck> acks = new HashMap<>();

        @Override
        public void record(StoredAck ack) {
            acks.put(ack.submissionId(), ack);
        }

        @Override
        public Optional<StoredAck> find(String submissionId) {
            return Optional.ofNullable(acks.get(submissionId));
        }
    }
}
