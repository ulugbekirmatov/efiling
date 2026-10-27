package com.irs.mef.filing;

import com.irs.mef.ats.AtsScenario;
import com.irs.mef.xml.XmlValidator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Offline. No Spring. No network. */
class ReturnComposerTest {

    @Test
    void composedReturnIsSchemaValidReportingAgent() {
        ClientReturnBody orchid = ClientReturnBody.load(AtsScenario.ORCHID_941.clientBody());
        Originator standIn = ReportingAgentOriginatorStandIn.pyramosShaped();

        ReturnComposer ret = ReturnComposer.compose(
                standIn, orchid, Clock.system(ZoneId.of("America/New_York")));

        XmlValidator.ValidationResult xsd = XmlValidator.validate(ret.xml(), ClasspathSchemas.root(FormSpec.F941));
        assertTrue(xsd.isValid(), xsd::report);

        ReturnComposer.Structure s = ret.structure();
        assertEquals("ReportingAgent", s.originatorTypeCd());
        assertEquals(standIn.efin(), s.originatorEfin());
        assertTrue(s.hasReportingAgentPinGrp());
        assertFalse(s.hasOnlineFilerPinGrp());
        assertFalse(s.hasPractitionerPinGrp());
        assertTrue(s.hasReportingAgent94XFilerGrp());
        assertEquals(standIn.identity().ein(), s.agentEin());
        assertEquals(orchid.filerEin(), s.filerEin());
        assertNotEquals(s.agentEin(), s.filerEin());
        assertEquals("2026Q1v4.0", s.returnVersion());
        assertEquals(ReturnComposer.RAPIN_ENTERED_BY, s.rapinEnteredByCd());
        assertEquals(ReturnComposer.JURAT, s.juratDisclosureCd());
        assertFalse(ret.xml().contains("10219201"), "ASID must not appear in the Return");
        assertFalse(ret.xml().contains("OnlineFilerPINGrp"));
        assertFalse(ret.xml().contains("OnlineFiler"));
        assertTrue(ret.xml().contains("xmlns:efile=\"" + Efile.NAMESPACE + "\""),
                "X0000-008 requires xmlns:efile on Return");
        assertFalse(ret.xml().contains("<Filer xmlns="),
                "Filer must inherit the Return default namespace");
    }

    @Test
    void loadRejectsScenario1ReturnRoot() {
        ReportingAgentValidationException ex = assertThrows(
                ReportingAgentValidationException.class,
                () -> ClientReturnBody.load(AtsScenario.repoPath("test-scenarios/941-scenario-1-orchid-q1-2026/Return941-Scenario1.xml")));
        assertEquals("xml", ex.field());
    }

    @Test
    void standInIsNotFiveDigitEtinOrTenDigitPin() {
        Originator standIn = ReportingAgentOriginatorStandIn.pyramosShaped();
        assertEquals(6, standIn.efin().value().length());
        assertEquals(5, standIn.pin().value().length());
        assertEquals(8, standIn.softwareIds().forRelease(SchemaRelease.Y2026Q1_V4).value().length());
        assertNotEquals("238689", standIn.efin().value());
        assertNotEquals("1234567890", standIn.pin().value());
        assertNotEquals("12345678", standIn.softwareIds().forRelease(SchemaRelease.Y2026Q1_V4).value());
    }

    @Test
    void agentEinDiffersFromFilerEin() {
        ClientReturnBody orchid = ClientReturnBody.load(AtsScenario.ORCHID_941.clientBody());
        Originator standIn = ReportingAgentOriginatorStandIn.pyramosShaped();
        assertEquals("003000004", orchid.filerEin().value());
        assertNotEquals(orchid.filerEin(), standIn.identity().ein());
    }
}
