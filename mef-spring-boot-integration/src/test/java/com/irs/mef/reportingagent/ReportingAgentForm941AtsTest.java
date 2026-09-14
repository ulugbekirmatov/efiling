package com.irs.mef.reportingagent;

import com.irs.mef.config.MefSdkConfig;
import com.irs.mef.dto.LoginResponse;
import com.irs.mef.newsend.NewSendSubmissionService;
import com.irs.mef.newsend.NewSendSubmitResult;
import com.irs.mef.newsend.domain.NewSendOutcome;
import com.irs.mef.service.MefClientService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Clock;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
@EnabledIfSystemProperty(named = "mef.integration.test.enabled", matches = "true")
class ReportingAgentForm941AtsTest {

    @Autowired
    MefClientService login;
    @Autowired
    MefSdkConfig sdk;
    @Autowired
    ReportingAgentOriginatorFactory originators;
    @Autowired
    NewSendSubmissionService newSend;

    @Test
    void submitsOneReportingAgent941ToAts() {
        ReportingAgentOriginator pyramos = originators.fromEnvironment();
        ReportingAgentAtsGate.open(sdk, pyramos);

        Client941Body orchid = Client941Body.load(OrchidQ1_2026.CLIENT_BODY);
        ReportingAgentReturn ret = ReportingAgentReturn.compose(
                pyramos, orchid, Clock.system(ZoneId.of("America/New_York")));

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
    }
}
