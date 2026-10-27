package com.irs.mef.filing;

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
    private static final XsdReturnValidator VALIDATOR = new XsdReturnValidator();

    static Stream<Arguments> quarterlyScenarios() {
        return Stream.of(
                arguments(AtsScenario.ORCHID_941, "941", FormSpec.F941, "003000004",
                        List.of("IRS941")),
                arguments(AtsScenario.MARIGOLD_941_SCHEDULE_B, "941", FormSpec.F941, "003333330",
                        List.of("IRS941", "IRS941ScheduleB")),
                arguments(AtsScenario.DAFFODIL_941_SCHEDULE_R_8974, "941", FormSpec.F941, "003222220",
                        List.of("IRS941", "IRS941ScheduleR", "IRS8974")),
                arguments(AtsScenario.ORCHID_941X, "941X", FormSpec.F941X, "003000004",
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
    void composesAValidQuarterlyReturnComposer(AtsScenario scenario,
                                                     String returnTypeCd,
                                                     FormSpec formType,
                                                     String filerEin,
                                                     List<String> documents) {
        ReturnComposer ret = compose(scenario);

        VALIDATOR.validate(formType, ret.xml());

        ReturnComposer.Structure header = ret.structure();
        assertReportingAgentHeader(header, returnTypeCd, filerEin);
        assertEquals("2026Q1v4.0", header.returnVersion());
        assertEquals("QuarterEndingDt", header.periodElement(), "EMPL-002-01 requires QuarterEndingDt on 941 and 941X");
        assertEquals("2026-03", header.periodValue());
        assertDocuments(ret, documents);
        assertSubmitCommandAndManifest(ret, scenario, formType, filerEin, LocalDate.of(2026, 3, 31));
    }

    @ParameterizedTest(name = "scenario {0}")
    @MethodSource("annualScenarios")
    void composesAnAnnual940Return(AtsScenario scenario, String filerEin, List<String> documents) {
        skipIfPending(FormSpec.F940);
        ReturnComposer ret = compose(scenario);

        ReturnComposer.Structure header = ret.structure();
        assertReportingAgentHeader(header, "940", filerEin);
        assertEquals("2026v2.0", header.returnVersion());
        assertEquals("TaxYr", header.periodElement(), "ReturnHeader94x makes TaxYr the annual choice");
        assertEquals("2026", header.periodValue());
        assertFalse(ret.xml().contains("QuarterEndingDt"), ret.xml());
        assertDocuments(ret, documents);
        assertSubmitCommandAndManifest(ret, scenario, FormSpec.F940, filerEin, LocalDate.of(2026, 12, 31));
    }

    @ParameterizedTest(name = "scenario {0}")
    @MethodSource("annualScenarios")
    void anAnnual940ReturnIsSchemaValid(AtsScenario scenario, String filerEin, List<String> documents) {
        skipIfPending(FormSpec.F940);
        VALIDATOR.validate(FormSpec.F940, compose(scenario).xml());
    }

    private static void skipIfPending(FormSpec form) {
        if (form.schema() instanceof SchemaBinding.Pending pending) {
            assumeTrue(false, pending.why());
        }
    }

    private static ReturnComposer compose(AtsScenario scenario) {
        return ReturnComposer.compose(
                ReportingAgentOriginatorStandIn.pyramosShaped(), ClientReturnBody.load(scenario.clientBody()), FIXED);
    }

    private static void assertReportingAgentHeader(ReturnComposer.Structure header,
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

    private static void assertDocuments(ReturnComposer ret, List<String> documents) {
        for (String document : documents) {
            assertTrue(ret.xml().contains("<" + document + " "), document + " missing from ReturnData");
        }
    }

    private static void assertSubmitCommandAndManifest(ReturnComposer ret,
                                                       AtsScenario scenario,
                                                       FormSpec formType,
                                                       String filerEin,
                                                       LocalDate periodEnd) {
        SubmitCommand command = ret.toSubmitCommand("key", scenario.clientId());
        assertEquals(formType, command.formType());
        assertEquals(filerEin, command.clientEin().value());
        assertEquals(LocalDate.of(2026, 1, 1), command.taxPeriod().begin());
        assertEquals(periodEnd, command.taxPeriod().end());

        String manifest = ManifestXml.render(new Filing(
                new SubmissionId("0000002026286abc1234"), command, FIXED.instant()));
        assertTrue(manifest.contains("<FederalSubmissionTypeCd>" + formType.code() + "</FederalSubmissionTypeCd>"),
                manifest);
        assertTrue(manifest.contains("<TaxPeriodEndDt>" + periodEnd + "</TaxPeriodEndDt>"), manifest);
        assertTrue(manifest.contains("<TIN>" + filerEin + "</TIN>"), manifest);
    }

    @Test
    void a941ReturnIsRefusedByThe941XSchema() {
        ReturnComposer scenario1 = ReturnComposer.compose(
                ReportingAgentOriginatorStandIn.pyramosShaped(),
                ClientReturnBody.load(AtsScenario.ORCHID_941.clientBody()), FIXED);

        FilingValidationException ex = assertThrows(FilingValidationException.class,
                () -> VALIDATOR.validate(FormSpec.F941X, scenario1.xml()));
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

}
