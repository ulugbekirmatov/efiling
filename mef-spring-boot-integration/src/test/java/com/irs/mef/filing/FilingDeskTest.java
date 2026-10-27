package com.irs.mef.filing;

import com.irs.mef.ack.AckStore;
import com.irs.mef.ack.StoredAck;
import com.irs.mef.config.MefSdkConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Offline. Identical client bytes replay the journaled receipt and do not call the IRS. */
class FilingDeskTest {

    @Test
    void anIdenticalReplayMakesNoIrsCallIncludingAfterRejection() throws Exception {
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
        FilingReceipt replay = desk.file(ClientFiling.parse("orchid", body));

        assertEquals(1, irsCalls.get());
        assertInstanceOf(FilingOutcome.Rejected.class, first.outcome());
        assertEquals(first.id(), replay.id());
        assertInstanceOf(FilingOutcome.Rejected.class, replay.outcome());
        assertEquals(true, replay.replay());
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
    private static FilingDesk desk(IrsSession session) {
        MefSdkConfig sdk = new MefSdkConfig();
        sdk.setEnvironment("ATS");
        return new FilingDesk(
                session,
                sdk,
                ReportingAgentOriginatorStandIn.pyramosShaped(),
                new FilingProperties(),
                new InMemoryJournal(),
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
                mock(AckStore.class),
                Clock.fixed(Instant.parse("2026-10-13T14:00:00Z"), ZoneOffset.UTC));
    }
}
