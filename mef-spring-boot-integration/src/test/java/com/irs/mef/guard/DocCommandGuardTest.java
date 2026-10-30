package com.irs.mef.guard;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocCommandGuardTest {

    private static final Path REPO_ROOT = Path.of("..");
    private static final Pattern VERB_ARM = Pattern.compile("(?m)^\\s*([A-Za-z][A-Za-z0-9-]*)\\)\\s*$");
    private static final Pattern MEF_VERB = Pattern.compile("\\./mef\\s+([A-Za-z][A-Za-z0-9-]*)");
    @Test
    void docsDoNotSpellMavenJavaOrAnUnknownMefVerb() throws IOException {
        Set<String> verbs = verbs(Files.readString(REPO_ROOT.resolve("mef")));
        List<String> hits = new ArrayList<>();
        for (Path doc : trackedMarkdown()) {
            collect(doc, verbs, hits);
        }
        assertEquals("", String.join("\n", hits),
                "Fenced mvn/java/JAVA_HOME line, or ./mef verb the script does not define");
    }

    private static void collect(Path doc, Set<String> verbs, List<String> hits) throws IOException {
        List<String> lines = readLines(REPO_ROOT.resolve(doc));
        boolean fenced = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isFence(line)) {
                fenced = !fenced;
                continue;
            }
            int number = i + 1;
            if (fenced && forbiddenCommand(line)) {
                hits.add(doc + ":" + number + ": " + line.strip());
            }
            Matcher mef = MEF_VERB.matcher(line);
            while (mef.find()) {
                String verb = mef.group(1);
                if (!verbs.contains(verb)) {
                    hits.add(doc + ":" + number + ": ./mef " + verb + " is not a verb");
                }
            }
        }
    }

    private static boolean forbiddenCommand(String line) {
        String rest = line.stripLeading();
        if (rest.startsWith("$ ")) {
            rest = rest.substring(2).stripLeading();
        }
        return word(rest, "mvn")
                || word(rest, "java")
                || rest.startsWith("JAVA_HOME=")
                || word(rest, "export JAVA_HOME")
                || rest.startsWith("export JAVA_HOME=");
    }

    private static boolean word(String rest, String command) {
        if (!rest.startsWith(command)) {
            return false;
        }
        return rest.length() == command.length() || Character.isWhitespace(rest.charAt(command.length()));
    }

    private static boolean isFence(String line) {
        String trimmed = line.stripLeading();
        return trimmed.startsWith("```") || trimmed.startsWith("~~~");
    }

    private static Set<String> verbs(String script) {
        int start = script.indexOf("case \"$verb\" in");
        int end = script.indexOf("\nesac", start);
        if (start < 0 || end < 0) {
            throw new IllegalStateException("./mef has no case \"$verb\" block");
        }
        Set<String> verbs = new TreeSet<>();
        Matcher arms = VERB_ARM.matcher(script.substring(start, end));
        while (arms.find()) {
            verbs.add(arms.group(1));
        }
        if (verbs.isEmpty()) {
            throw new IllegalStateException("./mef case block has no verbs");
        }
        return verbs;
    }

    private static List<Path> trackedMarkdown() throws IOException {
        Process git = new ProcessBuilder("git", "ls-files", "-z", "*.md")
                .directory(REPO_ROOT.toFile()).redirectErrorStream(false).start();
        String output = new String(git.getInputStream().readAllBytes());
        try {
            if (git.waitFor() != 0) {
                throw new IOException("git ls-files failed");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException(interrupted);
        }
        return Set.of(output.split("\0")).stream()
                .filter(name -> !name.isEmpty())
                .sorted()
                .map(Path::of)
                .filter(path -> Files.isRegularFile(REPO_ROOT.resolve(path)))
                .collect(Collectors.toList());
    }

    private static List<String> readLines(Path file) throws IOException {
        try {
            return Files.readAllLines(file);
        } catch (MalformedInputException binary) {
            return List.of();
        }
    }
}
