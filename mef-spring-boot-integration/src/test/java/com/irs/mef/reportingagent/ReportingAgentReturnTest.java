package com.irs.mef.reportingagent;

import com.irs.mef.config.MefSdkConfig;
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
class ReportingAgentReturnTest {

    @Test
    void composedReturnIsSchemaValidReportingAgent() {
        Client941Body orchid = Client941Body.load(OrchidQ1_2026.CLIENT_BODY);
        ReportingAgentOriginator standIn = ReportingAgentOriginatorStandIn.pyramosShaped();

        ReportingAgentReturn ret = ReportingAgentReturn.compose(
                standIn, orchid, Clock.system(ZoneId.of("America/New_York")));

        XmlValidator.ValidationResult xsd = XmlValidator.validate(ret.xml(), ClasspathSchemas.return941());
        assertTrue(xsd.isValid(), xsd::report);

        ReportingAgentReturn.Structure s = ret.structure();
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
        assertEquals(ReportingAgentReturn.RAPIN_ENTERED_BY, s.rapinEnteredByCd());
        assertEquals(ReportingAgentReturn.JURAT, s.juratDisclosureCd());
        assertFalse(ret.xml().contains("10219201"), "ASID must not appear in the Return");
        assertFalse(ret.xml().contains("OnlineFilerPINGrp"));
        assertFalse(ret.xml().contains("OnlineFiler"));
    }

    @Test
    void loadRejectsScenario1ReturnRoot() {
        ReportingAgentValidationException ex = assertThrows(
                ReportingAgentValidationException.class,
                () -> Client941Body.load(OrchidQ1_2026.SCENARIO_1_RETURN));
        assertEquals("xml", ex.field());
    }

    @Test
    void standInIsNotFiveDigitEtinOrTenDigitPin() {
        ReportingAgentOriginator standIn = ReportingAgentOriginatorStandIn.pyramosShaped();
        assertEquals(6, standIn.efin().value().length());
        assertEquals(5, standIn.pin().value().length());
        assertEquals(8, standIn.softwareId().value().length());
        assertNotEquals("238689", standIn.efin().value());
        assertNotEquals("1234567890", standIn.pin().value());
        assertNotEquals("12345678", standIn.softwareId().value());
    }

    @Test
    void agentEinDiffersFromFilerEin() {
        Client941Body orchid = Client941Body.load(OrchidQ1_2026.CLIENT_BODY);
        ReportingAgentOriginator standIn = ReportingAgentOriginatorStandIn.pyramosShaped();
        assertEquals("003000004", orchid.filerEin().value());
        assertNotEquals(orchid.filerEin(), standIn.identity().ein());
    }

    @Test
    void fromEnvironmentRejectsOneWellEfin() {
        ReportingAgentOriginatorFactory factory = factoryWithEfin("238689");
        ReportingAgentValidationException ex = assertThrows(
                ReportingAgentValidationException.class, factory::fromEnvironment);
        assertEquals("efin", ex.field());
    }

    @Test
    void fromEnvironmentRejectsStandInEfin() {
        ReportingAgentOriginatorFactory factory = factoryWithEfin("000000");
        ReportingAgentValidationException ex = assertThrows(
                ReportingAgentValidationException.class, factory::fromEnvironment);
        assertEquals("efin", ex.field());
    }

    @Test
    void fromEnvironmentRejectsOrchidAgentEin() {
        ReportingAgentProperties properties = populatedProperties();
        properties.setEin("003000004");
        MefSdkConfig sdk = new MefSdkConfig();
        sdk.getAuthentication().setEfin("111111");
        ReportingAgentOriginatorFactory factory = new ReportingAgentOriginatorFactory(properties, sdk);
        ReportingAgentValidationException ex = assertThrows(
                ReportingAgentValidationException.class, factory::fromEnvironment);
        assertEquals("ein", ex.field());
    }

    @Test
    void fromEnvironmentFailsClosedOnMissingPin() {
        ReportingAgentProperties properties = populatedProperties();
        properties.setPin("");
        MefSdkConfig sdk = new MefSdkConfig();
        sdk.getAuthentication().setEfin("111111");
        ReportingAgentOriginatorFactory factory = new ReportingAgentOriginatorFactory(properties, sdk);
        ReportingAgentValidationException ex = assertThrows(
                ReportingAgentValidationException.class, factory::fromEnvironment);
        assertEquals("MEF_RA_PIN", ex.field());
    }

    private static ReportingAgentOriginatorFactory factoryWithEfin(String efin) {
        MefSdkConfig sdk = new MefSdkConfig();
        sdk.getAuthentication().setEfin(efin);
        return new ReportingAgentOriginatorFactory(populatedProperties(), sdk);
    }

    private static ReportingAgentProperties populatedProperties() {
        ReportingAgentProperties properties = new ReportingAgentProperties();
        properties.setPin("11111");
        properties.setSoftwareId("11111111");
        properties.setEin("111111111");
        properties.setBusinessName("PYRAMOS SOFTWARE LLC");
        properties.setNameControl("PYRA");
        properties.setAddressLine1("100 Market Street");
        properties.setCity("Philadelphia");
        properties.setState("PA");
        properties.setZip("19103");
        return properties;
    }
}
