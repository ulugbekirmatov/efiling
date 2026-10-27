package com.irs.mef.guard;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Docs may not name a Java symbol or a file that no longer exists. Agents kept leaving such
 * references behind after deleting a class. Mark a line deleted/retired to keep a historical mention.
 */
class DocReferenceGuardTest {

    private static final Path MODULE = Path.of("");
    private static final Path REPO_ROOT = Path.of("..");
    private static final String MODULE_DIR = "mef-spring-boot-integration/";

    private static final List<String> DOC_ROOTS = List.of("", MODULE_DIR, "test-scenarios/", "c2s-web/");
    private static final List<String> EXCLUDED_SEGMENTS = List.of(
            "Version_1", "94x-2026", "sdk-reference", "c2s/", "node_modules");
    private static final List<String> SOURCE_EXTENSIONS = List.of(".java", ".js", ".sh");

    private static final Pattern BACKTICKED = Pattern.compile("`([^`\\n]+)`");
    private static final Pattern SYMBOL = Pattern.compile(
            "[A-Z][A-Za-z0-9]{3,}(Test|Service|Controller|Gate|Preflight|Config|Store|Builder|Factory|Generator"
                    + "|Guard|Validator|Journal|Originator|Release|Properties)");
    private static final Pattern METHOD_SUFFIX = Pattern.compile("(\\(.*\\)|#.*)$");
    private static final Pattern PATH = Pattern.compile(
            "[A-Za-z0-9_./-]+\\.(java|sh|md|yml|xml|xsd|js|json|properties|tsv)(:\\d+(-\\d+)?)?");
    private static final Pattern EXEMPT = Pattern.compile(
            "deleted|retired|historical|superseded|no longer", Pattern.CASE_INSENSITIVE);

    @Test
    void docsDoNotNameMissingSymbolsOrPaths() throws IOException {
        List<Path> tracked = trackedFiles();
        String sourceText = readSources(tracked);
        Set<String> trackedNames = tracked.stream().map(path -> path.getFileName().toString())
                .collect(Collectors.toSet());
        List<String> hits = new ArrayList<>();
        for (Path doc : tracked) {
            if (isGuardedDoc(doc)) {
                collectHits(doc, sourceText, trackedNames, hits);
            }
        }
        assertEquals("", String.join("\n", hits), "Fix the doc or mark the line deleted/retired.");
    }

    private static void collectHits(Path doc, String sourceText, Set<String> trackedNames, List<String> hits) throws IOException {
        List<String> lines = readLines(REPO_ROOT.resolve(doc));
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (EXEMPT.matcher(line).find()) {
                continue;
            }
            Matcher tokens = BACKTICKED.matcher(line);
            while (tokens.find()) {
                String token = tokens.group(1).trim();
                String problem = problemWith(token, doc, sourceText, trackedNames);
                if (problem != null) {
                    hits.add(doc + ":" + (i + 1) + "  `" + token + "`  " + problem
                            + "; fix the doc or mark the line deleted/retired");
                }
            }
        }
    }

    private static String problemWith(String token, Path doc, String sourceText, Set<String> trackedNames) {
        String symbol = METHOD_SUFFIX.matcher(token).replaceFirst("");
        if (SYMBOL.matcher(symbol).matches()) {
            return sourceText.contains(symbol) ? null : "names a symbol absent from every tracked .java/.js/.sh";
        }
        if (PATH.matcher(token).matches() && !token.contains("...") && !pathExists(token, doc, trackedNames)) {
            return "names a path that does not exist";
        }
        return null;
    }

    /** A bare file name (no slash) is a shorthand for the file; it counts when any tracked file has that name. */
    private static boolean pathExists(String token, Path doc, Set<String> trackedNames) {
        String path = token.replaceFirst(":\\d+(-\\d+)?$", "");
        if (!path.contains("/") && trackedNames.contains(path)) {
            return true;
        }
        Path docDir = doc.getParent() == null ? Path.of("") : doc.getParent();
        return Files.exists(REPO_ROOT.resolve(path))
                || Files.exists(REPO_ROOT.resolve(docDir).resolve(path))
                || Files.exists(REPO_ROOT.resolve(MODULE_DIR).resolve(path));
    }

    private static boolean isGuardedDoc(Path doc) {
        String path = doc.toString().replace('\\', '/');
        if (!path.endsWith(".md")) {
            return false;
        }
        boolean rootLevel = !path.contains("/");
        boolean inRoot = rootLevel || DOC_ROOTS.stream().anyMatch(root -> !root.isEmpty() && path.startsWith(root));
        return inRoot && EXCLUDED_SEGMENTS.stream().noneMatch(path::contains);
    }

    private static String readSources(List<Path> tracked) throws IOException {
        List<String> chunks = new ArrayList<>();
        for (Path file : tracked) {
            if (SOURCE_EXTENSIONS.stream().anyMatch(file.toString()::endsWith)) {
                chunks.addAll(readLines(REPO_ROOT.resolve(file)));
            }
        }
        return String.join("\n", chunks);
    }

    private static List<Path> trackedFiles() throws IOException {
        Process git = new ProcessBuilder("git", "ls-files", "-z")
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
        Set<String> names = Set.of(output.split("\0"));
        return names.stream().filter(name -> !name.isEmpty()).sorted().map(Path::of)
                .filter(path -> Files.isRegularFile(REPO_ROOT.resolve(path))).collect(Collectors.toList());
    }

    private static List<String> readLines(Path file) throws IOException {
        try {
            return Files.readAllLines(file);
        } catch (MalformedInputException binary) {
            return List.of();
        }
    }
}
