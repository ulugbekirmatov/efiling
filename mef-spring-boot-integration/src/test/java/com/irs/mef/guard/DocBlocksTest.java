package com.irs.mef.guard;

import com.irs.mef.ats.AtsScenario;
import com.irs.mef.filing.SchemaRelease;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocBlocksTest {

    private static final Path CLAUDE = Path.of("..", "CLAUDE.md");
    private static final Path ORIGINATOR = Path.of("src/main/java/com/irs/mef/filing/Originator.java");
    private static final Path APPLICATION_YML = Path.of("src/main/resources/application.yml");
    private static final Pattern QUOTED_ENV = Pattern.compile("\"(MEF_[A-Z0-9_]+)\"");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)");
    private static final List<String> BLOCKS = List.of("routes", "env", "scenarios");

    @Test
    void generatedBlocksMatchTheCode() throws Exception {
        String renderedRoutes = routes();
        String renderedEnv = env();
        String renderedScenarios = scenarios();
        String text = Files.readString(CLAUDE);
        boolean update = "true".equals(System.getProperty("docs.update"));
        String next = text;
        List<String> diffs = new ArrayList<>();
        for (String name : BLOCKS) {
            String expected = switch (name) {
                case "routes" -> renderedRoutes;
                case "env" -> renderedEnv;
                case "scenarios" -> renderedScenarios;
                default -> throw new IllegalStateException(name);
            };
            Block block = find(next, name);
            if (block == null) {
                diffs.add("missing <!-- generated:" + name + " -->");
                continue;
            }
            if (!expected.equals(block.body())) {
                if (update) {
                    next = next.substring(0, block.bodyStart()) + expected + next.substring(block.bodyEnd());
                } else {
                    diffs.add(name + "\n--- rendered ---\n" + expected + "--- in CLAUDE.md ---\n" + block.body());
                }
            }
        }
        if (update && !next.equals(text)) {
            Files.writeString(CLAUDE, next);
        }
        assertEquals("", String.join("\n", diffs));
    }

    private static String routes() throws ReflectiveOperationException, IOException {
        String context = contextPath();
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class, false));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class, false));
        List<String> lines = new ArrayList<>();
        for (BeanDefinition bean : scanner.findCandidateComponents("com.irs.mef")) {
            Class<?> type = Class.forName(bean.getBeanClassName());
            RequestMapping typeMapping = type.getAnnotation(RequestMapping.class);
            List<String> roots = typeMapping == null
                    ? List.of("")
                    : paths(typeMapping.path(), typeMapping.value());
            for (Method method : type.getDeclaredMethods()) {
                for (Mapping mapping : mappings(method)) {
                    for (String root : roots) {
                        for (String tail : mapping.paths()) {
                            lines.add(mapping.verb() + " " + join(context, root, tail));
                        }
                    }
                }
            }
        }
        lines.sort(String::compareTo);
        if (lines.isEmpty()) {
            throw new IllegalStateException("no HTTP mappings on com.irs.mef controllers");
        }
        return String.join("\n", lines) + "\n";
    }

    private static String env() throws ReflectiveOperationException, IOException {
        TreeSet<String> names = new TreeSet<>();
        Matcher quoted = QUOTED_ENV.matcher(Files.readString(ORIGINATOR));
        while (quoted.find()) {
            names.add(quoted.group(1));
        }
        Method key = SchemaRelease.class.getDeclaredMethod("softwareIdEnvKey");
        key.setAccessible(true);
        for (SchemaRelease release : SchemaRelease.values()) {
            names.add((String) key.invoke(release));
        }
        Matcher placeholders = PLACEHOLDER.matcher(Files.readString(APPLICATION_YML));
        while (placeholders.find()) {
            names.add(placeholders.group(1));
        }
        return String.join("\n", names) + "\n";
    }

    private static String scenarios() {
        StringBuilder out = new StringBuilder("| id | form | title |\n|---|---|---|\n");
        for (AtsScenario scenario : AtsScenario.all()) {
            out.append("| ").append(scenario.id())
                    .append(" | ").append(scenario.form())
                    .append(" | ").append(scenario.title())
                    .append(" |\n");
        }
        return out.toString();
    }

    private static String contextPath() throws IOException {
        for (String line : Files.readAllLines(APPLICATION_YML)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("context-path:")) {
                return trimmed.substring("context-path:".length()).trim();
            }
        }
        return "";
    }

    private static List<Mapping> mappings(Method method) {
        List<Mapping> found = new ArrayList<>();
        GetMapping get = method.getAnnotation(GetMapping.class);
        if (get != null) {
            found.add(new Mapping("GET", paths(get.path(), get.value())));
        }
        PostMapping post = method.getAnnotation(PostMapping.class);
        if (post != null) {
            found.add(new Mapping("POST", paths(post.path(), post.value())));
        }
        PutMapping put = method.getAnnotation(PutMapping.class);
        if (put != null) {
            found.add(new Mapping("PUT", paths(put.path(), put.value())));
        }
        PatchMapping patch = method.getAnnotation(PatchMapping.class);
        if (patch != null) {
            found.add(new Mapping("PATCH", paths(patch.path(), patch.value())));
        }
        DeleteMapping delete = method.getAnnotation(DeleteMapping.class);
        if (delete != null) {
            found.add(new Mapping("DELETE", paths(delete.path(), delete.value())));
        }
        RequestMapping request = method.getAnnotation(RequestMapping.class);
        if (request != null && get == null && post == null && put == null && patch == null && delete == null) {
            String verb = request.method().length == 0 ? "ANY" : request.method()[0].name();
            found.add(new Mapping(verb, paths(request.path(), request.value())));
        }
        return found;
    }

    private static List<String> paths(String[] path, String[] value) {
        String[] chosen = path.length > 0 ? path : value;
        if (chosen.length == 0) {
            return List.of("");
        }
        return List.of(chosen);
    }

    private static String join(String context, String root, String tail) {
        String joined = (context + "/" + root + "/" + tail).replaceAll("/{2,}", "/");
        if (joined.length() > 1 && joined.endsWith("/")) {
            joined = joined.substring(0, joined.length() - 1);
        }
        return joined.startsWith("/") ? joined : "/" + joined;
    }

    private static Block find(String text, String name) {
        String start = "<!-- generated:" + name + " -->\n";
        int from = text.indexOf(start);
        if (from < 0) {
            return null;
        }
        int bodyStart = from + start.length();
        int bodyEnd = text.indexOf("<!-- /generated -->", bodyStart);
        if (bodyEnd < 0) {
            return null;
        }
        return new Block(bodyStart, bodyEnd, text.substring(bodyStart, bodyEnd));
    }

    private record Mapping(String verb, List<String> paths) {
    }

    private record Block(int bodyStart, int bodyEnd, String body) {
    }
}
