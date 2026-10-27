package com.irs.mef.filing;

import com.irs.mef.ack.StoredAck;
import com.irs.mef.live.LiveIrsTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Sends the ATS scenario named by -Dmef.ats.scenario. Run through run-ra-ats-test.sh.
 * No default: a forgotten flag would re-file scenario 1, which ATS already accepted.
 */
@SpringBootTest
@LiveIrsTest
class ReportingAgentAtsScenarioTest {

    static final String SCENARIO_PROPERTY = "mef.ats.scenario";

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

    @Test
    void submitsOneReportingAgentScenarioToAts() throws Exception {
        String requested = System.getProperty(SCENARIO_PROPERTY);
        if (requested == null || requested.isBlank()) {
            fail("Set -D" + SCENARIO_PROPERTY + "=<scenario id>; this test sends a real ATS submission");
        }
        AtsScenario scenario = AtsScenario.byId(requested);
        String clientBody = Files.readString(scenario.clientBody());
        FilingReceipt receipt = desk.file(ClientFiling.parse(scenario.clientId(), clientBody));

        FilingOutcome outcome = receipt.outcome();
        if (outcome instanceof FilingOutcome.Rejected rejected) {
            fail("ATS rejected the container (nothing filed): " + rejected.fault());
        }
        if (outcome instanceof FilingOutcome.Indeterminate indeterminate) {
            fail("Indeterminate send; do not resubmit; reconcile submissionId "
                    + receipt.id().value() + ": " + indeterminate.fault());
        }
        assertInstanceOf(FilingOutcome.Transmitted.class, outcome);

        System.out.println("RA ATS transmitted submissionId=" + receipt.id().value());
        StoredAck ack = waitForAck(receipt.id());
        System.out.println("RA ATS ackType=" + ack.ackType()
                + " details=" + ack.details()
                + " errorCodes=" + ack.errorCodes());
        assertEquals("Accepted", ack.ackType(),
                "IRS did not accept submissionId " + receipt.id().value() + ": " + ack.errorCodes());
    }

    /** GetAck by submission id, not GetNewAcks. IRS validation is minutes behind Transmitted. */
    private StoredAck waitForAck(SubmissionId submissionId) throws InterruptedException {
        Duration budget = Duration.ofMinutes(6);
        Duration pause = Duration.ofSeconds(30);
        long deadline = System.currentTimeMillis() + budget.toMillis();
        while (System.currentTimeMillis() < deadline) {
            Optional<StoredAck> ack = desk.ack(submissionId);
            if (ack.isPresent()) {
                return ack.get();
            }
            Thread.sleep(pause.toMillis());
        }
        fail("No ACK after " + budget.toMinutes() + " minutes. Poll GetAck later. submissionId="
                + submissionId.value());
        return null;
    }
}
