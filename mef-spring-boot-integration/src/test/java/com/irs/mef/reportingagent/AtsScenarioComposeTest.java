package com.irs.mef.reportingagent;

import com.irs.mef.newsend.NewSendProperties;
import com.irs.mef.newsend.domain.FormType;
import com.irs.mef.newsend.domain.NewSendFiling;
import com.irs.mef.newsend.domain.NewSendManifestXml;
import com.irs.mef.newsend.domain.NewSendSubmissionId;
import com.irs.mef.newsend.domain.NewSendSubmitCommand;
import com.irs.mef.newsend.error.NewSendValidationException;
import com.irs.mef.newsend.validate.XsdNewSendReturnXmlValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Offline. Every ATS scenario fixture composes into a Return with the header and manifest a
 * Reporting Agent filing of that form needs. Quarterly returns must also pass the app's own
 * validator in ENFORCE mode; annual 940 returns do once the 2026v2.0 schemas are bundled.
 * A missing fixture fails, naming its path.
 */
class AtsScenarioComposeTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-10-13T14:00:00Z"), ZoneOffset.UTC);
    private static final XsdNewSendReturnXmlValidator VALIDATOR =
            new XsdNewSendReturnXmlValidator(enforcing());

    static Stream<Arguments> quarterlyScenarios() {
        return Stream.of(
                arguments(AtsScenario.ORCHID_941, "941", FormType.F941, "003000004",
                        List.of("IRS941")),
                arguments(AtsScenario.MARIGOLD_941_SCHEDULE_B, "941", FormType.F941, "003333330",
                        List.of("IRS941", "IRS941ScheduleB")),
                arguments(AtsScenario.DAFFODIL_941_SCHEDULE_R_8974, "941", FormType.F941, "003222220",
                        List.of("IRS941", "IRS941ScheduleR", "IRS8974")),
                arguments(AtsScenario.ORCHID_941X, "941X", FormType.F941X, "003000004",
                        List.of("IRS941X")));
    }

    static Stream<Arguments> annualScenarios() {
        return Stream.of(
                arguments(AtsScenario.ZINNIA_940, "003000011", List.of("IRS940")),
                arguments(AtsScenario.POPPY_940_SCHEDULE_A, "003000001", List.of("IRS940", "IRS940ScheduleA")),
                arguments(AtsScenario.CROCUS_940_SCHEDULE_R, "003000002", List.of("IRS940", "IRS940ScheduleR")));
    }

    @ParameterizedTest(name = "scenario {0}")
    @MethodSource("quarterlyScenarios")
    void composesAValidQuarterlyReportingAgentReturn(AtsScenario scenario,
                                                     String returnTypeCd,
                                                     FormType formType,
                                                     String filerEin,
                                                     List<String> documents) {
        ReportingAgentReturn ret = compose(scenario);

        VALIDATOR.validate(formType, ret.xml());

        ReportingAgentReturn.Structure header = ret.structure();
        assertReportingAgentHeader(header, returnTypeCd, filerEin);
        assertEquals("2026Q1v4.0", header.returnVersion());
        assertEquals("QuarterEndingDt", header.periodElement(), "EMPL-002-01 requires QuarterEndingDt on 941 and 941X");
        assertEquals("2026-03", header.periodValue());
        assertDocuments(ret, documents);
        assertSubmitCommandAndManifest(ret, scenario, formType, filerEin, LocalDate.of(2026, 3, 31));
    }

    @ParameterizedTest(name = "scenario {0}")
    @MethodSource("annualScenarios")
    void composesAnAnnual940ReportingAgentReturn(AtsScenario scenario, String filerEin, List<String> documents) {
        ReportingAgentReturn ret = compose(scenario);

        ReportingAgentReturn.Structure header = ret.structure();
        assertReportingAgentHeader(header, "940", filerEin);
        assertEquals("2026v2.0", header.returnVersion());
        assertEquals("TaxYr", header.periodElement(), "ReturnHeader94x makes TaxYr the annual choice");
        assertEquals("2026", header.periodValue());
        assertFalse(ret.xml().contains("QuarterEndingDt"), ret.xml());
        assertDocuments(ret, documents);
        assertSubmitCommandAndManifest(ret, scenario, FormType.F940, filerEin, LocalDate.of(2026, 12, 31));
    }

    @ParameterizedTest(name = "scenario {0}")
    @MethodSource("annualScenarios")
    void anAnnual940ReturnIsSchemaValid(AtsScenario scenario, String filerEin, List<String> documents) {
        String schemaRoot = FormType.F940.schemaRoot().orElseThrow();
        assumeTrue(getClass().getClassLoader().getResource(schemaRoot) != null,
                "Bundled schema missing from classpath: " + schemaRoot);

        VALIDATOR.validate(FormType.F940, compose(scenario).xml());
    }

    private static ReportingAgentReturn compose(AtsScenario scenario) {
        return ReportingAgentReturn.compose(
                ReportingAgentOriginatorStandIn.pyramosShaped(), ClientReturnBody.load(scenario.clientBody()), FIXED);
    }

    private static void assertReportingAgentHeader(ReportingAgentReturn.Structure header,
                                                   String returnTypeCd,
                                                   String filerEin) {
        assertEquals(returnTypeCd, header.returnTypeCd());
        assertEquals("ReportingAgent", header.originatorTypeCd());
        assertEquals("000000", header.originatorEfin().value());
        assertEquals("000000000", header.agentEin().value());
        assertEquals(filerEin, header.filerEin().value());
        assertTrue(header.hasReportingAgentPinGrp());
        assertTrue(header.hasReportingAgent94XFilerGrp());
        assertFalse(header.hasOnlineFilerPinGrp());
        assertEquals("REPORTING AGENT", header.rapinEnteredByCd());
        assertEquals("REPORTING AGENT PIN", header.juratDisclosureCd());
    }

    private static void assertDocuments(ReportingAgentReturn ret, List<String> documents) {
        for (String document : documents) {
            assertTrue(ret.xml().contains("<" + document + " "), document + " missing from ReturnData");
        }
    }

    private static void assertSubmitCommandAndManifest(ReportingAgentReturn ret,
                                                       AtsScenario scenario,
                                                       FormType formType,
                                                       String filerEin,
                                                       LocalDate periodEnd) {
        NewSendSubmitCommand command = ret.toSubmitCommand("key", scenario.clientId(), false);
        assertEquals(formType, command.formType());
        assertEquals(filerEin, command.clientEin().value());
        assertEquals(LocalDate.of(2026, 1, 1), command.taxPeriod().begin());
        assertEquals(periodEnd, command.taxPeriod().end());

        String manifest = NewSendManifestXml.render(new NewSendFiling(
                new NewSendSubmissionId("0000002026286abc1234"), command, FIXED.instant()));
        assertTrue(manifest.contains("<FederalSubmissionTypeCd>" + formType.code() + "</FederalSubmissionTypeCd>"),
                manifest);
        assertTrue(manifest.contains("<TaxPeriodEndDt>" + periodEnd + "</TaxPeriodEndDt>"), manifest);
        assertTrue(manifest.contains("<TIN>" + filerEin + "</TIN>"), manifest);
    }

    @Test
    void a941ReturnIsRefusedByThe941XSchema() {
        ReportingAgentReturn scenario1 = ReportingAgentReturn.compose(
                ReportingAgentOriginatorStandIn.pyramosShaped(),
                ClientReturnBody.load(AtsScenario.ORCHID_941.clientBody()), FIXED);

        NewSendValidationException ex = assertThrows(NewSendValidationException.class,
                () -> VALIDATOR.validate(FormType.F941X, scenario1.xml()));
        assertTrue(ex.getMessage().startsWith("Return XML failed 941X schema validation: "), ex.getMessage());
    }

    @Test
    void anUnknownScenarioIdIsRefused() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AtsScenario.byId("5"));
        assertEquals("Unknown ATS scenario '5'. Must be one of: 1, 2, 3, 4, 940-1, 940-2, 940-3", ex.getMessage());
    }

    /** The journal and the c2s-web viewer group sends by this clientId; it must not drift. */
    @Test
    void aScenarioIdKeepsItsClientId() {
        assertEquals("ats-scenario-1-orchid-941", AtsScenario.byId("1").clientId());
        assertEquals("ats-scenario-4-orchid-941x", AtsScenario.byId("4").clientId());
        assertEquals("ats-scenario-940-1-zinnia-940", AtsScenario.byId("940-1").clientId());
    }

    @Test
    void aMissingFixtureFailsNamingItsPath() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> AtsScenario.repoPath("test-scenarios/ats-ty2026/no-such-scenario/client-return-body.xml"));
        assertTrue(ex.getMessage().startsWith("Fixture not found: /"), ex.getMessage());
        assertTrue(ex.getMessage().endsWith("/test-scenarios/ats-ty2026/no-such-scenario/client-return-body.xml"),
                ex.getMessage());
    }

    private static NewSendProperties enforcing() {
        NewSendProperties properties = new NewSendProperties();
        properties.setXmlValidation(NewSendProperties.XmlValidationMode.ENFORCE);
        return properties;
    }
}
