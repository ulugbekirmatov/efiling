package com.irs.mef.filing;

import com.irs.mef.ack.AckStore;
import com.irs.mef.ack.StoredAck;
import com.irs.mef.ats.AtsRun;
import com.irs.mef.ats.AtsScenario;
import com.irs.mef.ats.AtsVerdict;
import com.irs.mef.ats.RejectLedger;
import com.irs.mef.config.MefSdkConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AtsRunDuplicateVerdictTest {

    @Test
    void aRefusedSendIsANotSentVerdict() throws Exception {
        InMemoryJournal journal = new InMemoryJournal();
        AckStore ackStore = mock(AckStore.class);
        when(ackStore.find(any())).thenReturn(Optional.empty());
        FilingDesk desk = desk(journal, ackStore);
        String body = Files.readString(AtsScenario.ORCHID_941.clientBody());
        desk.file(ClientFiling.parse("already-there", body));

        AtsVerdict verdict = new AtsRun(desk, new RejectLedger(journal, ackStore), "ATS")
                .run(AtsScenario.ORCHID_941);

        assertInstanceOf(AtsVerdict.NotSent.class, verdict);
        assertEquals(4, verdict.exitCode());
    }

    @SuppressWarnings("unchecked")
    private static FilingDesk desk(Journal journal, AckStore ackStore) {
        IrsSession session = mock(IrsSession.class);
        when(session.call(any())).thenAnswer(invocation -> {
            IrsCall<FilingOutcome> call = invocation.getArgument(0);
            return call.run(new Object());
        });
        MefSdkConfig sdk = new MefSdkConfig();
        sdk.setEnvironment("ATS");
        Clock clock = Clock.fixed(Instant.parse("2026-10-13T14:00:00Z"), ZoneOffset.UTC);
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
                                cleared.prepared().postmark(),
                                TransmissionReceipt.TimestampSource.LOCAL, 1));
                    }

                    @Override
                    public Optional<StoredAck> getAck(Object serviceContext, String submissionId) {
                        return Optional.empty();
                    }
                },
                ackStore,
                clock,
                new IrsWire(clock, NoOpSendSubmissionsWireTap.INSTANCE));
    }
}
