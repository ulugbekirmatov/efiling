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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Offline. The duplicate-period guard keys on (EIN, form, period): a 941-X correcting a
 * transmitted 941 is a new filing, a second 941 for the same quarter is not.
 */
class NewSendDuplicatePeriodTest {

    private final AckStore ackStore = mock(AckStore.class);
    private final FilingDesk service = service(ackStore);

    @Test
    void a941XForATransmitted941PeriodIsSent() throws Exception {
        FilingReceipt original = service.file(ClientFiling.parse("orchid", body("1")));
        FilingReceipt correction = service.file(ClientFiling.parse("orchid-x", body("4")));

        assertInstanceOf(FilingOutcome.Transmitted.class, original.outcome());
        assertInstanceOf(FilingOutcome.Transmitted.class, correction.outcome());
        assertEquals(FormSpec.F941X,
                service.findBySubmissionId(correction.id().value()).orElseThrow().formType());
    }

    @Test
    void aSecond941ForTheSamePeriodIsRefused() throws Exception {
        FilingReceipt original = service.file(ClientFiling.parse("orchid", body("1")));

        FilingDuplicateException ex = assertThrows(FilingDuplicateException.class,
                () -> service.file(ClientFiling.parse("orchid-again", body("1"))));
        assertEquals("NEWSEND_PERIOD_ALREADY_FILED", ex.errorCode());
        assertEquals(List.of(original.id().value()), ex.conflictingSubmissionIds());
        assertTrue(ex.getMessage().contains("allowDuplicatePeriod=true"));
    }

    @Test
    void aRejectedAckDoesNotBlockThePeriod() throws Exception {
        FilingReceipt original = service.file(ClientFiling.parse("orchid", body("1")));
        when(ackStore.find(original.id().value())).thenReturn(java.util.Optional.of(
                rejectedAck(original.id().value())));

        FilingReceipt again = service.file(ClientFiling.parse("orchid-again", body("1")));

        assertInstanceOf(FilingOutcome.Transmitted.class, again.outcome());
        assertNotEquals(original.id(), again.id());
    }

    @Test
    void allowDuplicatePeriodFilesTheSamePeriodAgain() throws Exception {
        service.file(ClientFiling.parse("orchid", body("1")));

        FilingReceipt again = service.file(ClientFiling.parse("orchid-again", body("1"), true));

        assertInstanceOf(FilingOutcome.Transmitted.class, again.outcome());
    }

    private static String body(String scenarioId) throws Exception {
        return Files.readString(AtsScenario.byId(scenarioId).clientBody());
    }

    private static StoredAck rejectedAck(String submissionId) {
        return new StoredAck(
                null, submissionId, Instant.EPOCH, com.irs.mef.ack.AckSource.GET_ACK, "Rejected",
                null, null, List.of("R0000-058"), List.of(), null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private static FilingDesk service(AckStore ackStore) {
        when(ackStore.find(any())).thenReturn(Optional.empty());
        IrsSession session = mock(IrsSession.class);
        when(session.call(any())).thenAnswer(invocation -> {
            IrsCall<FilingOutcome> call = invocation.getArgument(0);
            return call.run(new Object());
        });
        MefSdkConfig sdk = new MefSdkConfig();
        sdk.setEnvironment("ATS");
        Clock clock = Clock.fixed(Instant.parse("2026-10-13T14:00:00Z"), ZoneOffset.UTC);
        TransmitGateway gateway = new TransmitGateway() {
            @Override
            public FilingOutcome transmit(Object serviceContext, Preflight.Cleared cleared) {
                return new FilingOutcome.Transmitted(new TransmissionReceipt(
                        "DEP-" + cleared.prepared().submissionId().value(),
                        cleared.prepared().submissionId(),
                        cleared.prepared().postmark(),
                        TransmissionReceipt.TimestampSource.LOCAL,
                        1));
            }

            @Override
            public Optional<StoredAck> getAck(Object serviceContext, String submissionId) {
                return Optional.empty();
            }
        };
        return new FilingDesk(
                session,
                sdk,
                ReportingAgentOriginatorStandIn.pyramosShaped(),
                new FilingProperties(),
                new InMemoryJournal(),
                new XsdReturnValidator(),
                gateway,
                ackStore,
                clock,
                new IrsWire(clock, NoOpSendSubmissionsWireTap.INSTANCE));
    }
}
