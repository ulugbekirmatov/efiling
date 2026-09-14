package com.irs.mef.reportingagent;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Path to the Orchid Q1 2026 client-body fixture.
 * Resolved from the repo root (parent of {@code mef-spring-boot-integration} when {@code user.dir}
 * is the Maven module).
 */
final class OrchidQ1_2026 {

    static final Path CLIENT_BODY = resolve(
            "test-scenarios/941-reporting-agent-orchid-q1-2026/Client941-Orchid-Q1-2026.xml");

    static final Path SCENARIO_1_RETURN = resolve(
            "test-scenarios/941-scenario-1-orchid-q1-2026/Return941-Scenario1.xml");

    private OrchidQ1_2026() {}

    private static Path resolve(String relativeToRepo) {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path fromModule = cwd.getParent() != null ? cwd.getParent().resolve(relativeToRepo) : null;
        if (fromModule != null && Files.exists(fromModule)) {
            return fromModule;
        }
        Path fromRepo = cwd.resolve(relativeToRepo);
        if (Files.exists(fromRepo)) {
            return fromRepo;
        }
        throw new IllegalStateException("Fixture not found: " + relativeToRepo + " (cwd=" + cwd + ")");
    }
}
