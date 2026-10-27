package com.irs.mef.reportingagent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AtsScenarioRegistryTest {
    @Test
    void everyRegisteredScenarioHasAUniqueIdAndAFixture() {
        List<String> ids = AtsScenario.all().stream().map(AtsScenario::id).toList();
        assertEquals(Set.copyOf(ids).size(), ids.size(), "duplicate scenario id in " + ids);
        AtsScenario.all().forEach(scenario -> scenario.clientBody());
    }

    @Test
    void theShellScriptReadsIdsFromScenariosJsonInsteadOfKeepingItsOwnList() throws IOException {
        String script = Files.readString(AtsScenario.repoPath("mef-spring-boot-integration/run-ra-ats-test.sh"));
        String hardCoded = script.lines().filter(line -> line.matches("\\s*[0-9][0-9a-z|-]*\\)\\s*;;"))
                .collect(Collectors.joining("\n"));
        assertFalse(script.contains("scenarios.json") && !hardCoded.isEmpty(), "hard-coded id list: " + hardCoded);
        assertEquals("", hardCoded, "run-ra-ats-test.sh keeps its own scenario list");
    }
}
