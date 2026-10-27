package com.irs.mef.reportingagent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * One TY2026 94x ATS scenario Pyramos sends as a Reporting Agent.
 * The list is every scenario directory under test-scenarios. The named constants are lookups for tests.
 */
record AtsScenario(String id, String shortName, String fixture) {
    private static final String MODULE_DIR = "mef-spring-boot-integration";
    /** id is digits, or a 3-digit form number, a hyphen, and digits. The slug starts with a letter. */
    private static final Pattern DIRECTORY_NAME = Pattern.compile(
            "^scenario-(?<id>[0-9]{3}-[0-9]+|[0-9]+)-(?<slug>[A-Za-z][A-Za-z0-9-]*)$");
    private static final List<AtsScenario> ALL = readDirectories();

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

    /** Plain numeric ids (1, 2, 3, 4) sort before form-prefixed ids (940-1). */
    static int compareIds(String left, String right) {
        boolean leftPrefixed = left.indexOf('-') >= 0;
        boolean rightPrefixed = right.indexOf('-') >= 0;
        if (leftPrefixed != rightPrefixed) {
            return leftPrefixed ? 1 : -1;
        }
        String[] leftParts = left.split("-", 2);
        String[] rightParts = right.split("-", 2);
        int major = Integer.compare(Integer.parseInt(leftParts[0]), Integer.parseInt(rightParts[0]));
        if (major != 0 || leftParts.length == 1) {
            return major;
        }
        return Integer.compare(Integer.parseInt(leftParts[1]), Integer.parseInt(rightParts[1]));
    }

    private static List<AtsScenario> readDirectories() {
        List<AtsScenario> scenarios = new ArrayList<>();
        Path root = repoRoot().resolve("test-scenarios");
        try (Stream<Path> tyDirs = Files.list(root)) {
            for (Path tyDir : tyDirs.filter(Files::isDirectory).toList()) {
                try (Stream<Path> children = Files.list(tyDir)) {
                    for (Path dir : children.filter(Files::isDirectory).toList()) {
                        String name = dir.getFileName().toString();
                        if (!name.startsWith("scenario-")) {
                            continue;
                        }
                        Matcher matcher = DIRECTORY_NAME.matcher(name);
                        if (!matcher.matches()) {
                            throw new IllegalStateException(
                                    "Scenario directory does not match scenario-<id>-<slug>: " + dir);
                        }
                        String relative = "test-scenarios/" + tyDir.getFileName() + "/" + name
                                + "/client-return-body.xml";
                        scenarios.add(new AtsScenario(matcher.group("id"), matcher.group("slug"), relative));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        scenarios.sort(Comparator.comparing(AtsScenario::id, AtsScenario::compareIds));
        return List.copyOf(scenarios);
    }

    /** Surefire runs with user.dir at the module; an IDE may run from the repo root. */
    static Path repoRoot() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return cwd.endsWith(MODULE_DIR) ? cwd.getParent() : cwd;
    }

    static Path repoPath(String relativeToRepo) {
        Path path = repoRoot().resolve(relativeToRepo);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Fixture not found: " + path);
        }
        return path;
    }
}
