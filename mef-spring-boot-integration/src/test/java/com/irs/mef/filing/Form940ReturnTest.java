package com.irs.mef.filing;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Offline. A 940 needs its own schema bundle and its own Software ID before it can be sent. */
class Form940ReturnTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-10-13T14:00:00Z"), ZoneOffset.UTC);

    @Test
    void aPending940IsRefusedWithItsReason() {
        SchemaBinding.Pending pending = (SchemaBinding.Pending) FormSpec.F940.schema();
        FilingValidationException refused = assertThrows(FilingValidationException.class,
                () -> new XsdReturnValidator().validate(FormSpec.F940, "<Return/>"));
        assertEquals(pending.why(), refused.getMessage());
    }

    @Test
    void a940IsRefusedWhenOnlyTheQuarterlySoftwareIdIsConfigured() {
        Originator standIn = ReportingAgentOriginatorStandIn.pyramosShaped();
        Originator quarterlyOnly = new Originator(standIn.efin(), standIn.pin(),
                SoftwareIds.of(SchemaRelease.Y2026Q1_V4, new SoftwareId("26990001")),
                standIn.filingIpv4Address(), standIn.identity());
        ClientReturnBody zinnia = ClientReturnBody.load(AtsScenario.ZINNIA_940.clientBody());

        ReportingAgentValidationException refused = assertThrows(ReportingAgentValidationException.class,
                () -> ReturnComposer.compose(quarterlyOnly, zinnia, FIXED));

        assertEquals("No Software ID configured for returnVersion 2026v2.0", refused.getMessage());
        assertEquals("26990001", ReturnComposer.compose(quarterlyOnly,
                ClientReturnBody.load(AtsScenario.byId("1").clientBody()), FIXED).structure().softwareId());
    }
}
