package com.irs.mef.reportingagent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

/** The TY2026 94x ATS scenarios Pyramos sends as a Reporting Agent, with their client-body fixtures. */
enum AtsScenario {
    ORCHID_941(1, "orchid-941",
            "test-scenarios/ats-ty2026/scenario-1-orchid-941/client-return-body.xml"),
    MARIGOLD_941_SCHEDULE_B(2, "marigold-941-schedule-b",
            "test-scenarios/ats-ty2026/scenario-2-marigold-941-schedule-b/client-return-body.xml"),
    DAFFODIL_941_SCHEDULE_R_8974(3, "daffodil-941-schedule-r-8974",
            "test-scenarios/ats-ty2026/scenario-3-daffodil-941-schedule-r-8974/client-return-body.xml"),
    ORCHID_941X(4, "orchid-941x",
            "test-scenarios/ats-ty2026/scenario-4-orchid-941x/client-return-body.xml");

    private static final String MODULE_DIR = "mef-spring-boot-integration";

    private final int number;
    private final String shortName;
    private final String fixture;

    AtsScenario(int number, String shortName, String fixture) {
        this.number = number;
        this.shortName = shortName;
        this.fixture = fixture;
    }

    int number() {
        return number;
    }

    String shortName() {
        return shortName;
    }

    /** NewSend clientId; the journal and inspector group sends by it. */
    String clientId() {
        return "ats-scenario-" + number + "-" + shortName;
    }

    Path clientBody() {
        return repoPath(fixture);
    }

    static AtsScenario byNumber(String raw) {
        return Arrays.stream(values())
                .filter(scenario -> Integer.toString(scenario.number).equals(raw))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown ATS scenario '" + raw
                        + "'. Must be one of: " + Arrays.stream(values())
                                .map(scenario -> Integer.toString(scenario.number))
                                .collect(Collectors.joining(", "))));
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
