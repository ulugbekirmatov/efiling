package com.irs.mef.newsend;

import com.irs.mef.config.MefSdkConfig;
import com.irs.mef.newsend.domain.Ein;
import com.irs.mef.newsend.domain.FormType;
import com.irs.mef.newsend.domain.NewSendOutcome;
import com.irs.mef.newsend.domain.NewSendReceipt;
import com.irs.mef.newsend.domain.NewSendSubmitCommand;
import com.irs.mef.newsend.domain.TaxPeriod;
import com.irs.mef.newsend.error.NewSendDuplicateException;
import com.irs.mef.newsend.journal.InMemoryNewSendSubmissionJournal;
import com.irs.mef.service.MefClientService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Offline. The duplicate-period guard keys on (EIN, form, period): a 941-X correcting a
 * transmitted 941 is a new filing, a second 941 for the same quarter is not.
 * MefClientService is mocked because it is the IRS session boundary.
 */
class NewSendDuplicatePeriodTest {

    private static final Ein ORCHID = new Ein("003000004");
    private static final TaxPeriod Q1_2026 = new TaxPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

    private final NewSendSubmissionService service = service();

    @Test
    void a941XForATransmitted941PeriodIsSent() {
        NewSendSubmitResult original = service.submit(command("key-941", FormType.F941));
        NewSendSubmitResult correction = service.submit(command("key-941x", FormType.F941X));

        assertInstanceOf(NewSendOutcome.Transmitted.class, original.outcome());
        assertInstanceOf(NewSendOutcome.Transmitted.class, correction.outcome());
        assertEquals(FormType.F941X,
                service.findByClientRequestId("key-941x").orElseThrow().formType());
    }

    @Test
    void aSecond941ForTheSamePeriodIsRefused() {
        NewSendSubmitResult original = service.submit(command("key-941", FormType.F941));

        NewSendDuplicateException ex = assertThrows(NewSendDuplicateException.class,
                () -> service.submit(command("key-941-again", FormType.F941)));
        assertEquals("NEWSEND_PERIOD_ALREADY_FILED", ex.errorCode());
        assertEquals(List.of(original.submissionId().value()), ex.conflictingSubmissionIds());
    }

    private static NewSendSubmitCommand command(String key, FormType formType) {
        return new NewSendSubmitCommand(key, "orchid", ORCHID, formType, Q1_2026,
                "<Return xmlns=\"http://www.irs.gov/efile\"/>", false);
    }

    private static NewSendSubmissionService service() {
        MefClientService session = mock(MefClientService.class);
        when(session.isLoggedIn()).thenReturn(true);
        when(session.getCurrentServiceContext()).thenReturn(new Object());
        MefSdkConfig sdk = new MefSdkConfig();
        sdk.setEnvironment("ATS");
        sdk.getAuthentication().setEfin("000000");
        Clock clock = Clock.fixed(Instant.parse("2026-10-13T14:00:00Z"), ZoneOffset.UTC);
        return new NewSendSubmissionService(
                session,
                sdk,
                new NewSendProperties(),
                new InMemoryNewSendSubmissionJournal(),
                (formType, returnXml) -> { },
                (serviceContext, filing) -> new NewSendOutcome.Transmitted(new NewSendReceipt(
                        "DEP-" + filing.command().clientRequestId(), filing.submissionId(),
                        filing.postmark(), NewSendReceipt.TimestampSource.LOCAL, 1)),
                clock);
    }
}
