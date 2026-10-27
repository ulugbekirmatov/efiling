package com.irs.mef.reportingagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One TY2026 94x ATS scenario Pyramos sends as a Reporting Agent. The list is read from the two scenarios.json
 * registries, so adding a scenario there is the only edit; the named constants below are lookups for tests.
 */
record AtsScenario(String id, String shortName, String fixture) {
    private static final String MODULE_DIR = "mef-spring-boot-integration";
    private static final List<String> REGISTRY_DIRS = List.of(
            "test-scenarios/ats-ty2026", "test-scenarios/ats-ty2026-annual");
    private static final List<AtsScenario> ALL = readRegistries();

    static final AtsScenario ORCHID_941 = byId("1");
    static final AtsScenario MARIGOLD_941_SCHEDULE_B = byId("2");
    static final AtsScenario DAFFODIL_941_SCHEDULE_R_8974 = byId("3");
    static final AtsScenario ORCHID_941X = byId("4");
    static final AtsScenario ZINNIA_940 = byId("940-1");
    static final AtsScenario POPPY_940_SCHEDULE_A = byId("940-2");
    static final AtsScenario CROCUS_940_SCHEDULE_R = byId("940-3");

    static List<AtsScenario> all() {
        return ALL;
    }

    /** NewSend clientId; the journal and inspector group sends by it. */
    String clientId() {
        return "ats-scenario-" + id + "-" + shortName;
    }

    Path clientBody() {
        return repoPath(fixture);
    }

    static AtsScenario byId(String raw) {
        return ALL.stream()
                .filter(scenario -> scenario.id.equals(raw))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown ATS scenario '" + raw
                        + "'. Must be one of: " + ALL.stream().map(AtsScenario::id).collect(Collectors.joining(", "))));
    }

    /** Quarterly entries carry a numeric "number", annual entries a string "id". */
    private static List<AtsScenario> readRegistries() {
        List<AtsScenario> scenarios = new ArrayList<>();
        try {
            for (String dir : REGISTRY_DIRS) {
                JsonNode registry = new ObjectMapper().readTree(repoPath(dir + "/scenarios.json").toFile());
                for (JsonNode entry : registry.get("scenarios")) {
                    String id = entry.has("id") ? entry.get("id").asText() : entry.get("number").asText();
                    scenarios.add(new AtsScenario(id, entry.get("slug").asText(),
                            dir + "/" + entry.get("dir").asText() + "/client-return-body.xml"));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return List.copyOf(scenarios);
    }

    /** Surefire runs with user.dir at the module; an IDE may run from the repo root. */
    static Path repoPath(String relativeToRepo) {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path repoRoot = cwd.endsWith(MODULE_DIR) ? cwd.getParent() : cwd;
        Path path = repoRoot.resolve(relativeToRepo);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Fixture not found: " + path);
        }
        return path;
    }
}
