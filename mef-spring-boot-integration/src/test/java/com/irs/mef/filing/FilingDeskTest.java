package com.irs.mef.filing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irs.mef.ats.AtsScenario;
import com.irs.mef.ack.AckStore;
import com.irs.mef.ack.StoredAck;
import com.irs.mef.config.MefSdkConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FilingDeskTest {

    @Test
    void aSoapRejectionReleasesTheKeyAndSendsAgain() throws Exception {
        AtomicInteger irsCalls = new AtomicInteger();
        IrsSession session = mock(IrsSession.class);
        when(session.call(any())).thenAnswer(invocation -> {
            irsCalls.incrementAndGet();
            return new FilingOutcome.Rejected(new IrsFault(
                    "NEWSEND_IRS_REJECTED", "refused", null, null, null));
        });
        FilingDesk desk = desk(session);
        String body = Files.readString(AtsScenario.ORCHID_941.clientBody());
        ClientFiling filing = ClientFiling.parse("orchid", body);

        FilingReceipt first = desk.file(filing);
        FilingReceipt second = desk.file(ClientFiling.parse("orchid", body));

        assertEquals(2, irsCalls.get());
        assertInstanceOf(FilingOutcome.Rejected.class, first.outcome());
        assertInstanceOf(FilingOutcome.Rejected.class, second.outcome());
        assertNotEquals(first.id(), second.id());
        assertEquals(false, second.replay());
    }

    @Test
    void aTransmittedFilingReplaysWithoutAnIrsCall() throws Exception {
        AtomicInteger irsCalls = new AtomicInteger();
        FilingDesk desk = desk(sessionThatRuns(irsCalls));
        String body = Files.readString(AtsScenario.ORCHID_941.clientBody());

        FilingReceipt first = desk.file(ClientFiling.parse("orchid", body));
        FilingReceipt replay = desk.file(ClientFiling.parse("orchid", body));

        assertEquals(1, irsCalls.get());
        assertEquals(first.id(), replay.id());
        assertEquals(true, replay.replay());
        assertInstanceOf(FilingOutcome.Transmitted.class, replay.outcome());
    }

    @Test
    void anIndeterminateFilingReplaysWithoutAnIrsCall() throws Exception {
        AtomicInteger irsCalls = new AtomicInteger();
        IrsSession session = mock(IrsSession.class);
        when(session.call(any())).thenAnswer(invocation -> {
            irsCalls.incrementAndGet();
            return new FilingOutcome.Indeterminate(new IrsFault(
                    "NEWSEND_INDETERMINATE", "unknown", null, null, null));
        });
        FilingDesk desk = desk(session);
        String body = Files.readString(AtsScenario.ORCHID_941.clientBody());

        FilingReceipt first = desk.file(ClientFiling.parse("orchid", body));
        FilingReceipt replay = desk.file(ClientFiling.parse("orchid", body));

        assertEquals(1, irsCalls.get());
        assertEquals(first.id(), replay.id());
        assertEquals(true, replay.replay());
        assertInstanceOf(FilingOutcome.Indeterminate.class, replay.outcome());
    }

    @Test
    void theSameBytesFileOnceInAtsAndOnceInPrd() throws Exception {
        AtomicInteger irsCalls = new AtomicInteger();
        MefSdkConfig sdk = new MefSdkConfig();
        sdk.setEnvironment("ATS");
        FilingDesk desk = desk(sessionThatRuns(irsCalls), sdk, new InMemoryJournal());
        String body = Files.readString(AtsScenario.ORCHID_941.clientBody());

        FilingReceipt ats = desk.file(ClientFiling.parse("orchid", body));
        sdk.setEnvironment("PRD");
        FilingReceipt prd = desk.file(ClientFiling.parse("orchid", body));

        assertEquals(2, irsCalls.get());
        assertEquals(false, ats.replay());
        assertEquals(false, prd.replay());
        assertNotEquals(ats.id(), prd.id());
    }

    @Test
    void aBusySessionAbandonsTheCreatedRow() throws Exception {
        IrsSession session = mock(IrsSession.class);
        when(session.call(any())).thenThrow(new FilingBusyException(30));
        InMemoryJournal journal = new InMemoryJournal();
        FilingDesk desk = desk(session, new MefSdkConfig(), journal);
        String body = Files.readString(AtsScenario.ORCHID_941.clientBody());

        FilingBusyException busy = assertThrows(FilingBusyException.class,
                () -> desk.file(ClientFiling.parse("orchid", body)));

        assertEquals(30L, busy.retryAfterSeconds());
        List<SubmissionRecord> rows = journal.findByClientId("orchid");
        assertEquals(1, rows.size());
        assertEquals(JournalState.ABANDONED, rows.get(0).state());
        assertEquals("NEWSEND_SESSION_BUSY", rows.get(0).fault().code());
    }

    @Test
    void aFailedJournalWriteKeepsTheRowOpenAndBlocksRepeats() throws Exception {
        AtomicInteger irsCalls = new AtomicInteger();
        InMemoryJournal inner = new InMemoryJournal();
        Journal failingComplete = new DelegatingJournal(inner) {
            @Override
            public void complete(SubmissionId id, FilingOutcome outcome, Instant at) {
                throw new IllegalStateException("disk full");
            }
        };
        FilingDesk desk = desk(sessionThatRuns(irsCalls), atsConfig(), failingComplete);
        String body = Files.readString(AtsScenario.ORCHID_941.clientBody());

        FilingReceipt first = desk.file(ClientFiling.parse("orchid", body));
        FilingDuplicateException repeat = assertThrows(FilingDuplicateException.class,
                () -> desk.file(ClientFiling.parse("orchid", body)));

        assertInstanceOf(FilingOutcome.Transmitted.class, first.outcome());
        assertFalse(first.journalDurable());
        assertEquals(JournalState.CREATED, inner.findByClientId("orchid").get(0).state());
        assertEquals("NEWSEND_KEY_UNRESOLVED", repeat.errorCode());
        assertEquals(1, irsCalls.get());
    }

    @Test
    void aRefusedSendInterruptedByACrashComesBackIndeterminate(@TempDir Path temp) throws Exception {
        Path journalPath = temp.resolve("journal.jsonl");
        AtomicInteger irsCalls = new AtomicInteger();
        FileJournal crashesBeforeWrite = new FileJournal(journalPath, new ObjectMapper()) {
            @Override
            public synchronized void complete(SubmissionId id, FilingOutcome outcome, Instant at) {
                throw new IllegalStateException("JVM dies here");
            }
        };
        String body = Files.readString(AtsScenario.ORCHID_941.clientBody());
        FilingReceipt refused = desk(refusing(irsCalls), atsConfig(), crashesBeforeWrite)
                .file(ClientFiling.parse("orchid", body));

        FileJournal afterRestart = new FileJournal(journalPath, new ObjectMapper());
        FilingReceipt retry = desk(refusing(irsCalls), atsConfig(), afterRestart)
                .file(ClientFiling.parse("orchid", body));

        assertInstanceOf(FilingOutcome.Rejected.class, refused.outcome());
        assertEquals(JournalState.INDETERMINATE, afterRestart.findByClientId("orchid").get(0).state());
        assertTrue(retry.replay());
        assertInstanceOf(FilingOutcome.Indeterminate.class, retry.outcome());
        assertEquals(1, irsCalls.get());
    }

    @SuppressWarnings("unchecked")
    private static IrsSession sessionThatRuns(AtomicInteger irsCalls) {
        IrsSession session = mock(IrsSession.class);
        when(session.call(any())).thenAnswer(invocation -> {
            irsCalls.incrementAndGet();
            IrsCall<FilingOutcome> call = invocation.getArgument(0);
            return call.run(new Object());
        });
        return session;
    }

    private static IrsSession refusing(AtomicInteger irsCalls) {
        IrsSession session = mock(IrsSession.class);
        when(session.call(any())).thenAnswer(invocation -> {
            irsCalls.incrementAndGet();
            return new FilingOutcome.Rejected(new IrsFault(
                    "NEWSEND_IRS_REJECTED", "refused", null, null, null));
        });
        return session;
    }

    private static MefSdkConfig atsConfig() {
        MefSdkConfig sdk = new MefSdkConfig();
        sdk.setEnvironment("ATS");
        return sdk;
    }

    private static FilingDesk desk(IrsSession session) {
        return desk(session, atsConfig(), new InMemoryJournal());
    }

    private static FilingDesk desk(IrsSession session, MefSdkConfig sdk, Journal journal) {
        Clock clock = Clock.fixed(Instant.parse("2026-10-13T14:00:00Z"), ZoneOffset.UTC);
        AckStore ackStore = mock(AckStore.class);
        when(ackStore.find(any())).thenReturn(java.util.Optional.empty());
        return new FilingDesk(
                session,
                sdk,
                ReportingAgentOriginatorStandIn.pyramosShaped(),
                new FilingProperties(),
                journal,
                new XsdReturnValidator(),
                new TransmitGateway() {
                    @Override
                    public FilingOutcome transmit(Object serviceContext, Preflight.Cleared cleared) {
                        return new FilingOutcome.Transmitted(new TransmissionReceipt(
                                "DEP", cleared.prepared().submissionId(),
                                cleared.prepared().postmark(), TransmissionReceipt.TimestampSource.LOCAL, 1));
                    }

                    @Override
                    public java.util.Optional<StoredAck> getAck(Object serviceContext, String submissionId) {
                        return java.util.Optional.empty();
                    }
                },
                ackStore,
                clock,
                new IrsWire(clock, NoOpSendSubmissionsWireTap.INSTANCE));
    }

    private static class DelegatingJournal implements Journal {
        private final Journal inner;

        DelegatingJournal(Journal inner) {
            this.inner = inner;
        }

        @Override
        public Reservation reserve(SubmissionRecord created) {
            return inner.reserve(created);
        }

        @Override
        public void complete(SubmissionId id, FilingOutcome outcome, Instant at) {
            inner.complete(id, outcome, at);
        }

        @Override
        public void abandon(SubmissionId id, IrsFault fault, Instant at) {
            inner.abandon(id, fault, at);
        }

        @Override
        public java.util.Optional<SubmissionRecord> findBySubmissionId(SubmissionId id) {
            return inner.findBySubmissionId(id);
        }

        @Override
        public java.util.Optional<SubmissionRecord> findByClientRequestId(String key) {
            return inner.findByClientRequestId(key);
        }

        @Override
        public List<SubmissionRecord> findFiledFor(Ein ein, FormSpec form, TaxPeriod period) {
            return inner.findFiledFor(ein, form, period);
        }

        @Override
        public List<SubmissionRecord> findByClientId(String clientId) {
            return inner.findByClientId(clientId);
        }
    }
}
