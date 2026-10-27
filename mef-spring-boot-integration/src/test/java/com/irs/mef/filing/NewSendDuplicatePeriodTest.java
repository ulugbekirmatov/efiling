package com.irs.mef.filing;

import com.irs.mef.config.MefSdkConfig;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Offline. The duplicate-period guard keys on (EIN, form, period): a 941-X correcting a
 * transmitted 941 is a new filing, a second 941 for the same quarter is not.
 * IrsSession is mocked because it is the IRS session boundary.
 */
class NewSendDuplicatePeriodTest {

    private static final Ein ORCHID = new Ein("003000004");
    private static final TaxPeriod Q1_2026 = new TaxPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

    private final FilingDesk service = service();

    @Test
    void a941XForATransmitted941PeriodIsSent() {
        SubmitResult original = service.submit(command("key-941", FormType.F941));
        SubmitResult correction = service.submit(command("key-941x", FormType.F941X));

        assertInstanceOf(FilingOutcome.Transmitted.class, original.outcome());
        assertInstanceOf(FilingOutcome.Transmitted.class, correction.outcome());
        assertEquals(FormType.F941X,
                service.findByClientRequestId("key-941x").orElseThrow().formType());
    }

    @Test
    void aSecond941ForTheSamePeriodIsRefused() {
        SubmitResult original = service.submit(command("key-941", FormType.F941));

        FilingDuplicateException ex = assertThrows(FilingDuplicateException.class,
                () -> service.submit(command("key-941-again", FormType.F941)));
        assertEquals("NEWSEND_PERIOD_ALREADY_FILED", ex.errorCode());
        assertEquals(List.of(original.submissionId().value()), ex.conflictingSubmissionIds());
    }

    private static SubmitCommand command(String key, FormType formType) {
        return new SubmitCommand(key, "orchid", ORCHID, formType, Q1_2026,
                "<Return xmlns=\"http://www.irs.gov/efile\"/>", false);
    }

    @SuppressWarnings("unchecked")
    private static FilingDesk service() {
        IrsSession session = mock(IrsSession.class);
        when(session.call(any())).thenAnswer(invocation -> {
            IrsCall<FilingOutcome> body = invocation.getArgument(0);
            return body.run(new Object());
        });
        MefSdkConfig sdk = new MefSdkConfig();
        sdk.setEnvironment("ATS");
        sdk.getAuthentication().setEfin("000000");
        Clock clock = Clock.fixed(Instant.parse("2026-10-13T14:00:00Z"), ZoneOffset.UTC);
        return new FilingDesk(
                session,
                sdk,
                new FilingProperties(),
                new InMemoryJournal(),
                (formType, returnXml) -> { },
                (serviceContext, filing) -> new FilingOutcome.Transmitted(new FilingReceipt(
                        "DEP-" + filing.command().clientRequestId(), filing.submissionId(),
                        filing.postmark(), FilingReceipt.TimestampSource.LOCAL, 1)),
                clock);
    }
}
