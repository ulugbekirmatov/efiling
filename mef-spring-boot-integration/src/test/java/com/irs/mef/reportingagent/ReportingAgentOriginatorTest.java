package com.irs.mef.reportingagent;

import com.irs.mef.newsend.domain.Efin;
import com.irs.mef.newsend.domain.Ein;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportingAgentOriginatorTest {

    private static final String ACCEPTED_EFIN = "237861";
    private static final String PUBLIC_IP = "192.0.2.1";

    @Test
    void theEfinAtsAcceptedBuildsAnOriginator() {
        assertDoesNotThrow(() -> originator(ACCEPTED_EFIN, PUBLIC_IP));
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

    private static ReportingAgentOriginator originator(String efin, String filingIp) {
        return new ReportingAgentOriginator(
                new Efin(efin),
                new ReportingAgentPin("00000"),
                new SoftwareId("00000000"),
                filingIp,
                new ReportingAgentIdentity(
                        new Ein("000000000"),
                        "PYRAMOS SOFTWARE LLC",
                        new NameControl("PYRA"),
                        new UsAddress("100 Market Street", "Philadelphia", "PA", "19103")));
    }
}
