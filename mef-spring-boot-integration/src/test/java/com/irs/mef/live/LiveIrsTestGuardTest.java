package com.irs.mef.live;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A plain {@code mvn test} must never reach IRS. Live tests used to share one
 * -Dmef.integration.test.enabled switch, so arming one armed every other.
 */
class LiveIrsTestGuardTest {

    private static final Path TEST_SOURCES = Path.of("src/test/java");
    private static final String RETIRED_SWITCH = "mef.integration.test.enabled";
    private static final String IRS_LOGIN_CALL = ".login(";
    private static final String LIVE_ANNOTATION = "@LiveIrsTest";

    @Test
    void noTestUsesTheRetiredSharedSwitch() throws IOException {
        List<Path> offenders = testSources()
                .filter(source -> read(source).contains(RETIRED_SWITCH))
                .toList();

        assertEquals(List.of(), offenders,
                RETIRED_SWITCH + " armed every live test at once. Annotate the class with "
                        + "com.irs.mef.live.LiveIrsTest instead.");
    }

    @Test
    void everyTestThatLogsInToIrsIsALiveIrsTest() throws IOException {
        List<Path> offenders = testSources()
                .filter(source -> read(source).contains(IRS_LOGIN_CALL))
                .filter(source -> !read(source).contains(LIVE_ANNOTATION))
                .toList();

        assertEquals(List.of(), offenders,
                "These tests call MefClientService.login() without com.irs.mef.live.LiveIrsTest, "
                        + "so a plain mvn test would contact IRS.");
    }

    private static Stream<Path> testSources() throws IOException {
        return Files.walk(TEST_SOURCES)
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.getFileName().toString().equals("LiveIrsTestGuardTest.java"));
    }

    private static String read(Path source) {
        try {
            return Files.readString(source);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + source, e);
        }
    }
}
