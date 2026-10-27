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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Offline. The duplicate-period guard keys on (EIN, form, period): a 941-X correcting a
 * transmitted 941 is a new filing, a second 941 for the same quarter is not.
 */
class NewSendDuplicatePeriodTest {

    private final FilingDesk service = service();

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
    }

    private static String body(String scenarioId) throws Exception {
        return Files.readString(AtsScenario.byId(scenarioId).clientBody());
    }

    @SuppressWarnings("unchecked")
    private static FilingDesk service() {
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
                mock(AckStore.class),
                clock,
                new IrsWire(clock, NoOpSendSubmissionsWireTap.INSTANCE));
    }
}
