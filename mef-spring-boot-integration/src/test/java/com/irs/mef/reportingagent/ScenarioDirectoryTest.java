package com.irs.mef.reportingagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioDirectoryTest {
    /** id is digits, or a 3-digit form number, a hyphen, and digits. The slug starts with a letter. */
    private static final Pattern SCENARIO_DIR = Pattern.compile(
            "^scenario-(?<id>[0-9]{3}-[0-9]+|[0-9]+)-(?<slug>[A-Za-z][A-Za-z0-9-]*)$");
    private static final List<String> IDS = List.of("1", "2", "3", "4", "940-1", "940-2", "940-3");

    @Test
    void everyScenarioDirectoryMatchesTheContract() throws IOException {
        List<String> ids = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        for (Path dir : scenarioDirs()) {
            String name = dir.getFileName().toString();
            assertTrue(SCENARIO_DIR.matcher(name).matches(), name);
            assertTrue(Files.isRegularFile(dir.resolve("scenario.json")), name + "/scenario.json");
            assertTrue(Files.isRegularFile(dir.resolve("client-return-body.xml")), name + "/client-return-body.xml");
            assertTrue(Files.isRegularFile(dir.getParent().resolve("formats.json")),
                    dir.getParent().getFileName() + "/formats.json");
            JsonNode entry = mapper.readTree(dir.resolve("scenario.json").toFile());
            assertTrue(entry.isObject(), name);
            for (String derived : List.of("number", "id", "dir", "slug")) {
                assertFalse(entry.has(derived), name + " scenario.json still has " + derived);
            }
            ids.add(idOf(name));
        }
        ids.sort(ScenarioDirectoryTest::compareIds);
        assertEquals(IDS, ids);
        assertEquals(IDS, AtsScenario.all().stream().map(AtsScenario::id).toList());
    }

    @Test
    void theShellScriptListsScenarioDirectories() throws IOException {
        String script = Files.readString(AtsScenario.repoPath("mef-spring-boot-integration/run-ra-ats-test.sh"));
        String hardCoded = script.lines()
                .filter(line -> line.matches("\\s*[0-9][0-9a-z|-]*\\)\\s*;;"))
                .collect(Collectors.joining("\n"));
        assertEquals("", hardCoded, "run-ra-ats-test.sh keeps its own scenario list");
        assertFalse(script.contains("scenarios.json"), "run-ra-ats-test.sh still reads scenarios.json");
    }

    private static List<Path> scenarioDirs() throws IOException {
        List<Path> dirs = new ArrayList<>();
        Path root = AtsScenario.repoRoot().resolve("test-scenarios");
        try (Stream<Path> tyDirs = Files.list(root)) {
            for (Path tyDir : tyDirs.filter(Files::isDirectory).toList()) {
                try (Stream<Path> children = Files.list(tyDir)) {
                    for (Path child : children.filter(Files::isDirectory).toList()) {
                        if (child.getFileName().toString().startsWith("scenario-")) {
                            dirs.add(child);
                        }
                    }
                }
            }
        }
        return dirs;
    }

    private static String idOf(String name) {
        Matcher matcher = SCENARIO_DIR.matcher(name);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(name);
        }
        return matcher.group("id");
    }

    /** Plain numeric ids sort before form-prefixed ids such as 940-1. */
    private static int compareIds(String left, String right) {
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
}
