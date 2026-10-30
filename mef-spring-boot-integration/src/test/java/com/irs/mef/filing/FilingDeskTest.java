package com.irs.mef.filing;

import com.irs.mef.ats.AtsScenario;
import com.irs.mef.ack.AckStore;
import com.irs.mef.ack.StoredAck;
import com.irs.mef.config.MefSdkConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void preflightRefusesAPending940WithItsReason() throws Exception {
        FilingDesk desk = desk(mock(IrsSession.class));
        String body = Files.readString(AtsScenario.ZINNIA_940.clientBody());
        SchemaBinding.Pending pending = (SchemaBinding.Pending) FormSpec.F940.schema();

        FilingValidationException refused = assertThrows(FilingValidationException.class,
                () -> desk.preflight(ClientFiling.parse("zinnia", body)));

        assertEquals(pending.why(), refused.getMessage());
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

    private static FilingDesk desk(IrsSession session) {
        MefSdkConfig sdk = new MefSdkConfig();
        sdk.setEnvironment("ATS");
        return desk(session, sdk, new InMemoryJournal());
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
}
