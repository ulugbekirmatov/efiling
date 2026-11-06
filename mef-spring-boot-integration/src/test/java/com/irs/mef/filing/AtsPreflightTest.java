package com.irs.mef.filing;

import com.irs.mef.ack.AckStore;
import com.irs.mef.ack.StoredAck;
import com.irs.mef.ats.AtsScenario;
import com.irs.mef.config.MefSdkConfig;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AtsPreflightTest {

    @Test
    void reportsOneScenario() throws Exception {
        String id = System.getProperty("mef.ats.scenario");
        assumeTrue(id != null && !id.isBlank(), "set -Dmef.ats.scenario=<id>");

        StringBuilder which = new StringBuilder();
        Originator originator = originator(which);
        Clock clock = Clock.systemUTC();
        IrsSession session = mock(IrsSession.class);
        when(session.call(any())).thenThrow(new AssertionError("preflight logged in"));
        MefSdkConfig sdk = new MefSdkConfig();
        sdk.setEnvironment("ATS");
        FilingDesk desk = new FilingDesk(
                session,
                sdk,
                originator,
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

        AtsScenario scenario = AtsScenario.byId(id);
        String body = Files.readString(scenario.clientBody());
        Preflight preflight = desk.preflight(ClientFiling.parse(scenario.clientId(), body));

        System.out.println("originator: " + which);
        System.out.println(preflight.manifestSchemaReport());
        System.out.println("archive bytes: " + preflight.archive().length);
        for (RuleResult result : preflight.rules()) {
            System.out.println(result.irsCode() + " " + (result.passed() ? "pass" : "FAIL " + result.detail()));
        }
        System.out.println("----- manifest -----");
        System.out.write(preflight.manifestXml());
        System.out.println("----- return -----");
        System.out.write(preflight.returnXml());
        System.out.println();
        assertTrue(preflight.failures().isEmpty(),
                () -> "local rules failed: " + preflight.failures());
    }

    private static Originator originator(StringBuilder which) {
        Dotenv dotenv = Dotenv.configure()
                .directory(System.getProperty("user.dir"))
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();
        boolean fromProcess = present(System.getenv("MEF_EFIN"));
        boolean fromFile = !fromProcess && present(dotenv.get("MEF_EFIN"));
        if (!fromProcess && !fromFile) {
            which.append("test stand-in");
            return ReportingAgentOriginatorStandIn.pyramosShaped();
        }
        MockEnvironment env = new MockEnvironment();
        dotenv.entries().forEach(entry -> env.setProperty(entry.getKey(), entry.getValue()));
        System.getenv().forEach(env::setProperty);
        which.append(fromProcess
                ? "Originator.fromEnvironment (process environment)"
                : "Originator.fromEnvironment (.env)");
        return Originator.fromEnvironment(env);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
