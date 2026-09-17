package com.irs.mef.reportingagent;

import com.irs.mef.config.MefSdkConfig;
import com.irs.mef.dto.AckResponse;
import com.irs.mef.dto.LoginResponse;
import com.irs.mef.exception.MefException;
import com.irs.mef.newsend.NewSendSubmissionService;
import com.irs.mef.newsend.NewSendSubmitResult;
import com.irs.mef.newsend.domain.NewSendOutcome;
import com.irs.mef.service.AcknowledgementService;
import com.irs.mef.service.MefClientService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
@EnabledIfSystemProperty(named = "mef.integration.test.enabled", matches = "true")
class ReportingAgentForm941AtsTest {

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
    MefClientService login;
    @Autowired
    MefSdkConfig sdk;
    @Autowired
    ReportingAgentOriginatorFactory originators;
    @Autowired
    NewSendSubmissionService newSend;
    @Autowired
    AcknowledgementService acknowledgements;

    @Test
    void submitsOneReportingAgent941ToAts() throws InterruptedException {
        ReportingAgentOriginator pyramos = originators.fromEnvironment();
        ReportingAgentAtsGate.open(sdk, pyramos);

        Client941Body orchid = Client941Body.load(OrchidQ1_2026.CLIENT_BODY);
        ReportingAgentReturn ret = ReportingAgentReturn.compose(
                pyramos, orchid, Clock.system(ZoneId.of("America/New_York")));
        ReportingAgentReturn.Structure header = ret.structure();
        System.out.println("RA ATS originatorType=" + header.originatorTypeCd()
                + " efin=" + header.originatorEfin().value()
                + " softwareId=" + header.softwareId()
                + " filerEin=" + header.filerEin().value()
                + " agentEin=" + header.agentEin().value());

        LoginResponse session = login.login();
        assertTrue(session.isSuccess());

        NewSendSubmitResult result = newSend.submit(ret.toSubmitCommand(
                "ra-ats-orchid-q1-" + UUID.randomUUID(),
                "orchid-ats-scenario-1",
                true));
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
        AckResponse ack = waitForAck(submissionId);
        System.out.println("RA ATS ackType=" + ack.getAckType()
                + " details=" + ack.getDetails()
                + " errorCodes=" + ack.getErrorCodes());
    }

    /**
     * GetAck by submission id, not GetNewAcks. IRS validation is minutes behind Transmitted.
     */
    private AckResponse waitForAck(String submissionId) throws InterruptedException {
        Duration budget = Duration.ofMinutes(6);
        Duration pause = Duration.ofSeconds(30);
        long deadline = System.currentTimeMillis() + budget.toMillis();
        MefException last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                AckResponse ack = acknowledgements.getAcknowledgment(submissionId);
                assertNotNull(ack);
                return ack;
            } catch (MefException e) {
                last = e;
                if (!"ACK_NOT_FOUND".equals(e.getErrorCode())) {
                    throw e;
                }
                Thread.sleep(pause.toMillis());
            }
        }
        fail("No ACK after " + budget.toMinutes() + " minutes. Poll GetAck later. submissionId="
                + submissionId + " last=" + (last == null ? "none" : last.getErrorCode()));
        return null;
    }
}
