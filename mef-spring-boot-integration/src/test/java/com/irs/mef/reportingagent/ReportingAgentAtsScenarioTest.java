package com.irs.mef.reportingagent;

import com.irs.mef.ack.StoredAck;
import com.irs.mef.config.MefSdkConfig;
import com.irs.mef.filing.IrsSession;
import com.irs.mef.filing.IrsWire;
import com.irs.mef.live.LiveIrsTest;
import com.irs.mef.newsend.NewSendSubmissionService;
import com.irs.mef.newsend.NewSendSubmitResult;
import com.irs.mef.newsend.domain.NewSendOutcome;
import com.irs.mef.newsend.domain.NewSendSubmitCommand;
import com.irs.mef.newsend.validate.NewSendReturnXmlValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    IrsSession session;
    @Autowired
    IrsWire wire;
    @Autowired
    MefSdkConfig sdk;
    @Autowired
    ReportingAgentOriginatorFactory originators;
    @Autowired
    NewSendSubmissionService newSend;
    @Autowired
    NewSendReturnXmlValidator returnXmlValidator;

    @Test
    void submitsOneReportingAgentScenarioToAts() throws InterruptedException {
        String requested = System.getProperty(SCENARIO_PROPERTY);
        if (requested == null || requested.isBlank()) {
            fail("Set -D" + SCENARIO_PROPERTY + "=<scenario id>; this test sends a real ATS submission");
        }
        AtsScenario scenario = AtsScenario.byId(requested);
        ReportingAgentOriginator pyramos = originators.fromEnvironment();
        ReportingAgentAtsGate.open(sdk, pyramos);

        ClientReturnBody client = ClientReturnBody.load(scenario.clientBody());
        ReportingAgentReturn ret = ReportingAgentReturn.compose(
                pyramos, client, Clock.system(ZoneId.of("America/New_York")));
        ReportingAgentReturn.Structure header = ret.structure();
        System.out.println("RA ATS scenario=" + scenario.id() + " " + scenario.shortName()
                + " returnType=" + header.returnTypeCd()
                + " originatorType=" + header.originatorTypeCd()
                + " efin=" + header.originatorEfin().value()
                + " softwareId=" + header.softwareId()
                + " filerEin=" + header.filerEin().value()
                + " agentEin=" + header.agentEin().value());

        NewSendSubmitCommand command = ret.toSubmitCommand(
                "ra-ats-scenario-" + scenario.id() + "-" + scenario.shortName() + "-" + UUID.randomUUID(),
                scenario.clientId(),
                true);
        // Submit validates only after login; a missing bundle or a schema error must not open an IRS session.
        returnXmlValidator.validate(command.formType(), command.returnXml());

        assertTrue(session.probe().loggedIn());

        NewSendSubmitResult result = newSend.submit(command);
        assertFalse(result.replay());

        NewSendOutcome outcome = result.outcome();
        if (outcome instanceof NewSendOutcome.Rejected rejected) {
            fail("ATS rejected the container (nothing filed): " + rejected.fault());
        }
        if (outcome instanceof NewSendOutcome.Indeterminate indeterminate) {
            fail("Indeterminate send; do not resubmit; reconcile submissionId "
                    + result.submissionId().value() + ": " + indeterminate.fault());
        }
        assertInstanceOf(NewSendOutcome.Transmitted.class, outcome);

        String submissionId = result.submissionId().value();
        System.out.println("RA ATS transmitted submissionId=" + submissionId);
        StoredAck ack = waitForAck(submissionId);
        System.out.println("RA ATS ackType=" + ack.ackType()
                + " details=" + ack.details()
                + " errorCodes=" + ack.errorCodes());
        assertEquals("Accepted", ack.ackType(),
                "IRS did not accept submissionId " + submissionId + ": " + ack.errorCodes());
    }

    /**
     * GetAck by submission id, not GetNewAcks. IRS validation is minutes behind Transmitted.
     */
    private StoredAck waitForAck(String submissionId) throws InterruptedException {
        Duration budget = Duration.ofMinutes(6);
        Duration pause = Duration.ofSeconds(30);
        long deadline = System.currentTimeMillis() + budget.toMillis();
        while (System.currentTimeMillis() < deadline) {
            Optional<StoredAck> ack = session.call(ctx -> wire.getAck(ctx, submissionId));
            if (ack.isPresent()) {
                return ack.get();
            }
            Thread.sleep(pause.toMillis());
        }
        fail("No ACK after " + budget.toMinutes() + " minutes. Poll GetAck later. submissionId="
                + submissionId);
        return null;
    }
}
