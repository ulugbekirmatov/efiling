package com.irs.mef.filing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OriginatorTest {

    private static final String ACCEPTED_EFIN = "237861";
    private static final String PUBLIC_IP = "192.0.2.1";

    @Test
    void theEfinAtsAcceptedBuildsAnOriginator() {
        Originator originator = originator(ACCEPTED_EFIN, PUBLIC_IP);
        assertEquals(ACCEPTED_EFIN, originator.efin().value());
        assertTrue(originator.toString().contains("pin=*****"));
        assertFalse(originator.toString().contains("11111"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"102192", "210000", "320000", "440000", "530000"})
    void anOnlineFilerEfinIsRefusedBeforeItReachesIrs(String onlineFilerEfin) {
        ReportingAgentValidationException refused = assertThrows(ReportingAgentValidationException.class,
                () -> originator(onlineFilerEfin, PUBLIC_IP));
        assertEquals("efin", refused.field());
        assertTrue(refused.getMessage().contains("R0000-118-01"), refused.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "127.1.2.3", "0.0.0.0"})
    void aNonRoutableFilingIpIsRefused(String address) {
        ReportingAgentValidationException refused = assertThrows(ReportingAgentValidationException.class,
                () -> originator(ACCEPTED_EFIN, address));
        assertEquals("filingIpv4Address", refused.field());
        assertTrue(refused.getMessage().contains("R0000-244"), refused.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "::1", "256.1.1.1", "1.2.3"})
    void aFilingIpThatIsNotDottedIpv4IsRefused(String address) {
        ReportingAgentValidationException refused = assertThrows(ReportingAgentValidationException.class,
                () -> originator(ACCEPTED_EFIN, address));
        assertEquals("filingIpv4Address", refused.field());
    }

    @Test
    void fromEnvironmentRejectsOneWellEfin() {
        ReportingAgentValidationException ex = assertThrows(ReportingAgentValidationException.class,
                () -> Originator.fromEnvironment(environment("238689", "111111111"))); // repo-guard:allow onewell-id reason="refuses a stale .env that still has the OneWell EFIN" expires=2027-08-21 approved-by=ulugbekirmatov
        assertEquals("efin", ex.field());
        assertTrue(ex.getMessage().contains("OneWell"));
    }

    @Test
    void fromEnvironmentRejectsStandInEfin() {
        ReportingAgentValidationException ex = assertThrows(ReportingAgentValidationException.class,
                () -> Originator.fromEnvironment(environment("000000", "111111111")));
        assertEquals("efin", ex.field());
        assertTrue(ex.getMessage().contains("000000"));
    }

    @Test
    void fromEnvironmentRejectsOrchidAgentEin() {
        ReportingAgentValidationException ex = assertThrows(ReportingAgentValidationException.class,
                () -> Originator.fromEnvironment(environment(ACCEPTED_EFIN, "003000004")));
        assertEquals("ein", ex.field());
        assertTrue(ex.getMessage().contains("003000004"));
    }

    @Test
    void fromEnvironmentFailsClosedOnMissingPin() {
        MockEnvironment env = environment(ACCEPTED_EFIN, "111111111");
        env.setProperty("MEF_RA_PIN", "");
        ReportingAgentValidationException ex = assertThrows(ReportingAgentValidationException.class,
                () -> Originator.fromEnvironment(env));
        assertEquals("MEF_RA_PIN", ex.field());
    }

    private static Originator originator(String efin, String filingIp) {
        return new Originator(
                new Efin(efin),
                new ReportingAgentPin("11111"),
                SoftwareIds.of(SchemaRelease.Y2026Q1_V4, new SoftwareId("00000000")),
                filingIp,
                new ReportingAgentIdentity(
                        new Ein("000000000"),
                        "PYRAMOS SOFTWARE LLC",
                        new NameControl("PYRA"),
                        new UsAddress("100 Market Street", "Philadelphia", "PA", "19103")));
    }

    private static MockEnvironment environment(String efin, String ein) {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("MEF_EFIN", efin);
        env.setProperty("MEF_RA_PIN", "11111");
        env.setProperty("MEF_RA_FILING_IP", PUBLIC_IP);
        env.setProperty("MEF_RA_EIN", ein);
        env.setProperty("MEF_RA_NAME", "PYRAMOS SOFTWARE LLC");
        env.setProperty("MEF_RA_NAME_CONTROL", "PYRA");
        env.setProperty("MEF_RA_ADDRESS_LINE1", "100 Market Street");
        env.setProperty("MEF_RA_CITY", "Philadelphia");
        env.setProperty("MEF_RA_STATE", "PA");
        env.setProperty("MEF_RA_ZIP", "19103");
        env.setProperty(SchemaRelease.Y2026Q1_V4.softwareIdEnvKey(), "11111111");
        return env;
    }
}
