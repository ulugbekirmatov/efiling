package com.irs.mef.filing;

import com.irs.mef.ack.AckStore;
import com.irs.mef.ack.StoredAck;
import com.irs.mef.ats.AtsScenario;
import com.irs.mef.config.MefSdkConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class LocalRulesTest {

    private static final Pattern SUBMISSION_ID = Pattern.compile("<SubmissionId>([^<]+)</SubmissionId>");

    @Test
    void eachRuleFailsOnItsSample() throws IOException {
        for (LocalRule rule : LocalRules.all()) {
            Preflight sample = sample(rule.rejectedSampleClasspathDir());
            RuleResult result = rule.check(sample);
            assertFalse(result.passed(), rule.irsCode() + " passed on " + rule.rejectedSampleClasspathDir()
                    + " (" + result.detail() + ")");
        }
    }

    @Test
    void everyRulePassesOnQuarterlyPreflight() throws IOException {
        FilingDesk desk = desk();
        List<AtsScenario> quarterly = List.of(
                AtsScenario.ORCHID_941,
                AtsScenario.MARIGOLD_941_SCHEDULE_B,
                AtsScenario.DAFFODIL_941_SCHEDULE_R_8974,
                AtsScenario.ORCHID_941X);
        for (AtsScenario scenario : quarterly) {
            String body = Files.readString(scenario.clientBody());
            Preflight preflight = desk.preflight(ClientFiling.parse(scenario.clientId(), body));
            assertTrue(preflight.archive().length > 2
                    && preflight.archive()[0] == 'P' && preflight.archive()[1] == 'K',
                    scenario.id() + " archive");
            assertFalse(preflight.manifestSchemaReport().isBlank(), scenario.id());
            for (RuleResult result : preflight.rules()) {
                assertTrue(result.passed(), scenario.id() + " " + result.irsCode() + " " + result.detail());
            }
        }
    }

    private static Preflight sample(String classpathDir) throws IOException {
        String returnXml = resource(classpathDir + "/return.xml");
        String manifest = resource(classpathDir + "/manifest.xml");
        ClientFiling filing = ClientFiling.parse(
                "ats-scenario-1-orchid-941",
                Files.readString(AtsScenario.ORCHID_941.clientBody()));
        Matcher id = SUBMISSION_ID.matcher(manifest);
        if (!id.find()) {
            throw new IllegalStateException("sample manifest has no SubmissionId: " + classpathDir);
        }
        SubmitCommand command = new SubmitCommand(
                "sample", filing.clientId().value(), filing.filer(), filing.form(), filing.period(), returnXml);
        Filing prepared = new Filing(
                new SubmissionId(id.group(1)), command, Instant.parse("2026-10-13T14:00:00Z"));
        return Preflight.of(filing, prepared, manifest);
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = LocalRulesTest.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing classpath resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static FilingDesk desk() {
        MefSdkConfig sdk = new MefSdkConfig();
        sdk.setEnvironment("ATS");
        Clock clock = Clock.fixed(Instant.parse("2026-10-13T14:00:00Z"), ZoneOffset.UTC);
        return new FilingDesk(
                mock(IrsSession.class),
                sdk,
                ReportingAgentOriginatorStandIn.pyramosShaped(),
                new FilingProperties(),
                new InMemoryJournal(),
                new XsdReturnValidator(),
                new TransmitGateway() {
                    @Override
                    public FilingOutcome transmit(Object serviceContext, Preflight.Cleared cleared) {
                        throw new AssertionError("preflight logged in");
                    }

                    @Override
                    public java.util.Optional<StoredAck> getAck(Object serviceContext, String submissionId) {
                        throw new AssertionError("preflight logged in");
                    }
                },
                mock(AckStore.class),
                clock,
                new IrsWire(clock, NoOpSendSubmissionsWireTap.INSTANCE));
    }
}
