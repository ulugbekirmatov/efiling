package com.irs.mef.reportingagent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class AtsScenarioRegistryTest {
    private static final String QUARTERLY_DIR = "test-scenarios/ats-ty2026";
    private static final String ANNUAL_DIR = "test-scenarios/ats-ty2026-annual";
    private static final Pattern CASE_PATTERN = Pattern.compile("(?m)^\\s*([0-9][0-9a-z|-]*)\\)\\s*;;");

    @Test
    void theThreeScenarioListsAgree() throws IOException {
        Map<String, Set<String>> sources = new LinkedHashMap<>();
        sources.put("AtsScenario enum", Arrays.stream(AtsScenario.values()).map(AtsScenario::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        sources.put("run-ra-ats-test.sh case", shellCaseIds());
        Set<String> registryIds = new LinkedHashSet<>(registryIds(QUARTERLY_DIR));
        registryIds.addAll(registryIds(ANNUAL_DIR));
        sources.put("scenarios.json", registryIds);

        Set<String> all = new TreeSet<>();
        sources.values().forEach(all::addAll);
        StringBuilder problems = new StringBuilder();
        for (String id : all) {
            sources.forEach((name, ids) -> {
                if (!ids.contains(id)) {
                    problems.append("scenario '").append(id).append("' is missing from ").append(name).append('\n');
                }
            });
        }
        assertTrue(problems.isEmpty(), "ATS scenario lists disagree:\n" + problems);
    }

    private static Set<String> shellCaseIds() throws IOException {
        String script = Files.readString(AtsScenario.repoPath("mef-spring-boot-integration/run-ra-ats-test.sh"));
        Matcher matcher = CASE_PATTERN.matcher(script);
        if (!matcher.find()) {
            throw new IllegalStateException("No scenario case pattern found in run-ra-ats-test.sh");
        }
        return new LinkedHashSet<>(Arrays.asList(matcher.group(1).split("\\|")));
    }

    private static Set<String> registryIds(String dir) throws IOException {
        JsonNode scenarios = new ObjectMapper()
                .readTree(AtsScenario.repoPath(dir + "/scenarios.json").toFile()).get("scenarios");
        Set<String> ids = new LinkedHashSet<>();
        for (JsonNode scenario : scenarios) {
            ids.add(scenario.has("id") ? scenario.get("id").asText() : scenario.get("number").asText());
        }
        return ids;
    }
}
