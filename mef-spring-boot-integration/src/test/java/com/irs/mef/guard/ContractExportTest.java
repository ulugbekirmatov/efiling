package com.irs.mef.guard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.irs.mef.ats.AtsScenario;
import com.irs.mef.filing.JournalState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContractExportTest {

    private static final Path FACTS = Path.of("..", "c2s-web", "generated", "mef-facts.json");
    private static final Pattern ACK_STATUS = Pattern.compile(
            "\"([^\"]+)\"\\s*\\.equals\\([^\\n]*?ackType\\(\\)\\)");

    @Test
    void mefFactsMatchTheCode() throws IOException {
        String rendered = render();
        String current = Files.readString(FACTS);
        if (rendered.equals(current)) {
            return;
        }
        if ("true".equals(System.getProperty("docs.update"))) {
            Files.writeString(FACTS, rendered);
            return;
        }
        assertEquals(rendered, current);
    }

    private static String render() throws IOException {
        List<String> journal = new ArrayList<>();
        for (JournalState state : JournalState.values()) {
            journal.add(state.name());
        }
        return "{\n"
                + "  \"journalStates\": [\n"
                + joinObjects(journal, "    ", true)
                + "  ],\n"
                + "  \"ackStatuses\": [" + joinInline(ackStatuses()) + "],\n"
                + "  \"scenarioStates\": [" + joinInline(scenarioStates()) + "]\n"
                + "}\n";
    }

    private static List<String> ackStatuses() throws IOException {
        Set<String> found = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = ACK_STATUS.matcher(Files.readString(file));
                while (matcher.find()) {
                    found.add(matcher.group(1));
                }
            }
        }
        return List.copyOf(found);
    }

    private static List<String> scenarioStates() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Set<String> states = new LinkedHashSet<>();
        for (AtsScenario scenario : AtsScenario.all()) {
            JsonNode entry = mapper.readTree(scenario.clientBody().getParent().resolve("scenario.json").toFile());
            String state = entry.path("status").path("state").asText();
            if (!state.isEmpty()) {
                states.add(state);
            }
        }
        return List.copyOf(states);
    }

    private static String joinObjects(List<String> values, String indent, boolean trailingCommaOnAllButLast) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            out.append(indent).append('"').append(values.get(i)).append('"');
            if (trailingCommaOnAllButLast && i + 1 < values.size()) {
                out.append(',');
            }
            out.append('\n');
        }
        return out.toString();
    }

    private static String joinInline(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append('"').append(values.get(i)).append('"');
        }
        return out.toString();
    }
}
