package com.irs.mef.filing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Offline. A 940 needs its own schema bundle and its own Software ID before it can be sent. */
class Form940ReturnTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-10-13T14:00:00Z"), ZoneOffset.UTC);

    @ParameterizedTest(name = "mode {0}")
    @EnumSource(FilingProperties.XmlValidationMode.class)
    void everyValidationModeRefusesA940WhileItsBundleIsAbsent(FilingProperties.XmlValidationMode mode) {
        assumeFalse(ClasspathSchemas.bundled(SchemaRelease.Y2026_V2), "2026v2.0 bundle is present");
        String returnXml = ReturnComposer.compose(
                ReportingAgentOriginatorStandIn.pyramosShaped(),
                ClientReturnBody.load(AtsScenario.ZINNIA_940.clientBody()), FIXED).xml();
        FilingProperties properties = new FilingProperties();
        properties.setXmlValidation(mode);
        XsdReturnValidator validator = new XsdReturnValidator(properties);

        FilingConfigurationException ex = assertThrows(FilingConfigurationException.class,
                () -> validator.validate(FormType.F940, returnXml));

        assertEquals("Bundled schema missing from classpath: schemas/94x/2026v2.0/EmploymentTax/940/Return940.xsd",
                ex.getMessage());
    }

    @Test
    void a940IsRefusedWhenOnlyTheQuarterlySoftwareIdIsConfigured() {
        ReportingAgentOriginator standIn = ReportingAgentOriginatorStandIn.pyramosShaped();
        ReportingAgentOriginator quarterlyOnly = new ReportingAgentOriginator(standIn.efin(), standIn.pin(),
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
