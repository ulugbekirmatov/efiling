package com.irs.mef.ats;

import com.irs.mef.ack.AckStore;
import com.irs.mef.config.MefSdkConfig;
import com.irs.mef.filing.FilingDesk;
import com.irs.mef.filing.Journal;
import com.irs.mef.live.LiveIrsTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Sends the ATS scenario named by -Dmef.ats.scenario. Run through ./mef ats.
 * No default: a forgotten flag would re-file scenario 1, which ATS already accepted.
 * The shell, not this test, exits with the verdict: a reject is a result.
 */
@SpringBootTest
@LiveIrsTest
class AtsRunTest {

    static {
        String existing = System.getProperty("A2A_TOOLKIT_HOME");
        if (existing == null || existing.isBlank()) {
            System.setProperty(
                    "A2A_TOOLKIT_HOME",
                    Path.of(System.getProperty("user.dir"), "src/main/resources/mef_config")
                            .toAbsolutePath()
                            .toString());
        }
    }

    @Autowired
    FilingDesk desk;

    @Autowired
    Journal journal;

    @Autowired
    AckStore ackStore;

    @Autowired
    MefSdkConfig config;

    @Test
    void runsTheArmedScenario() throws Exception {
        String requested = System.getProperty("mef.ats.scenario");
        if (requested == null || requested.isBlank()) {
            fail("Set -Dmef.ats.scenario=<scenario id>; this test sends a real ATS submission and has no default");
        }
        AtsVerdict verdict = new AtsRun(desk, new RejectLedger(journal, ackStore), config.getEnvironment())
                .run(AtsScenario.byId(requested));
        assertNotNull(verdict);
    }
}
