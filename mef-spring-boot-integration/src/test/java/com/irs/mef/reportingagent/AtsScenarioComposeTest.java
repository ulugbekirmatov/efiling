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
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Offline. Every ATS scenario fixture composes into a Return the app's own validator accepts
 * in ENFORCE mode, with the header and manifest a Reporting Agent filing of that form needs.
 * A missing fixture fails, naming its path.
 */
class AtsScenarioComposeTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-10-13T14:00:00Z"), ZoneOffset.UTC);
    private static final XsdNewSendReturnXmlValidator VALIDATOR =
            new XsdNewSendReturnXmlValidator(enforcing());

    static Stream<Arguments> scenarios() {
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

    @ParameterizedTest(name = "scenario {0}")
    @MethodSource("scenarios")
    void composesAValidReportingAgentReturn(AtsScenario scenario,
                                            String returnTypeCd,
                                            FormType formType,
                                            String filerEin,
                                            List<String> documents) {
        ReportingAgentOriginator standIn = ReportingAgentOriginatorStandIn.pyramosShaped();
        ReportingAgentReturn ret = ReportingAgentReturn.compose(
                standIn, ClientReturnBody.load(scenario.clientBody()), FIXED);

        VALIDATOR.validate(formType, ret.xml());

        ReportingAgentReturn.Structure header = ret.structure();
        assertEquals(returnTypeCd, header.returnTypeCd());
        assertEquals("2026Q1v4.0", header.returnVersion());
        assertEquals("ReportingAgent", header.originatorTypeCd());
        assertEquals("000000", header.originatorEfin().value());
        assertEquals("000000000", header.agentEin().value());
        assertEquals(filerEin, header.filerEin().value());
        assertTrue(header.hasReportingAgentPinGrp());
        assertTrue(header.hasReportingAgent94XFilerGrp());
        assertFalse(header.hasOnlineFilerPinGrp());
        assertEquals("REPORTING AGENT", header.rapinEnteredByCd());
        assertEquals("REPORTING AGENT PIN", header.juratDisclosureCd());
        assertTrue(ret.xml().contains("<QuarterEndingDt>2026-03</QuarterEndingDt>"),
                "EMPL-002-01 requires QuarterEndingDt on 941 and 941X");
        for (String document : documents) {
            assertTrue(ret.xml().contains("<" + document + " "), document + " missing from ReturnData");
        }

        NewSendSubmitCommand command = ret.toSubmitCommand("key", scenario.clientId(), false);
        assertEquals(formType, command.formType());
        assertEquals(filerEin, command.clientEin().value());
        assertEquals(LocalDate.of(2026, 1, 1), command.taxPeriod().begin());
        assertEquals(LocalDate.of(2026, 3, 31), command.taxPeriod().end());

        String manifest = NewSendManifestXml.render(new NewSendFiling(
                new NewSendSubmissionId("0000002026286abc1234"), command, FIXED.instant()));
        assertTrue(manifest.contains("<FederalSubmissionTypeCd>" + returnTypeCd + "</FederalSubmissionTypeCd>"),
                manifest);
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
    void anUnknownScenarioNumberIsRefused() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AtsScenario.byNumber("5"));
        assertEquals("Unknown ATS scenario '5'. Must be one of: 1, 2, 3, 4", ex.getMessage());
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
