package com.irs.mef.reportingagent;

import com.irs.mef.newsend.domain.NewSendFiling;
import com.irs.mef.newsend.domain.NewSendManifestXml;
import com.irs.mef.newsend.domain.NewSendSubmissionId;
import com.irs.mef.newsend.domain.NewSendSubmitCommand;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Golden snapshots of each ATS scenario's composed Return and manifest.
 * The c2s-web scenario pages display these files; this test keeps them in step with the composer.
 */
class AtsScenarioSnapshotTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-10-13T14:00:00Z"), ZoneOffset.UTC);
    private static final String STALE =
            "Stale snapshot. Regenerate with: mvn test -Dtest=AtsScenarioSnapshotTest -Dats.snapshots.update=true";

    @ParameterizedTest
    @EnumSource(AtsScenario.class)
    void composedFormatsMatchSnapshots(AtsScenario scenario) throws IOException {
        ReportingAgentReturn ret = ReportingAgentReturn.compose(
                ReportingAgentOriginatorStandIn.pyramosShaped(),
                ClientReturnBody.load(scenario.clientBody()), FIXED);
        NewSendSubmitCommand command = ret.toSubmitCommand("key", scenario.clientId(), false);
        String manifest = NewSendManifestXml.render(new NewSendFiling(
                new NewSendSubmissionId("0000002026286abc1234"), command, FIXED.instant()));
        Path dir = scenario.clientBody().getParent();
        assertSnapshot(dir.resolve("composed-return.xml"), ret.xml());
        assertSnapshot(dir.resolve("manifest.xml"), manifest);
    }

    private static void assertSnapshot(Path file, String produced) throws IOException {
        String actual = produced.endsWith("\n") ? produced : produced + "\n";
        if ("true".equals(System.getProperty("ats.snapshots.update"))) {
            Files.writeString(file, actual, StandardCharsets.UTF_8);
            return;
        }
        if (!Files.isRegularFile(file)) {
            fail(file + " " + STALE);
        }
        assertEquals(Files.readString(file, StandardCharsets.UTF_8), actual, file + " " + STALE);
    }
}
