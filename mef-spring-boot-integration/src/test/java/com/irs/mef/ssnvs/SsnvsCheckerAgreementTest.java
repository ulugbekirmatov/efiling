package com.irs.mef.ssnvs;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs the independent checker against the Java sample request and its fake result. Skips only when
 * python3 is not on PATH.
 */
class SsnvsCheckerAgreementTest {

    @TempDir
    Path temp;

    @Test
    @DisplayName("python3 ssnvs_check.py accepts the sample request and the request/result pair")
    void checkerAgrees() throws IOException, InterruptedException {
        Assumptions.assumeTrue(python3Available(), "python3 is not on PATH");
        Path module = Path.of("ssnvs_check.py").toAbsolutePath().getParent();
        Assumptions.assumeTrue(Files.isRegularFile(module.resolve("ssnvs_check.py")), "ssnvs_check.py is missing");
        SsnvsSampleFilesTest.writeSamplePair(temp);
        Path request = temp.resolve(SsnvsSampleFilesTest.REQUEST);
        Path result = temp.resolve(SsnvsSampleFilesTest.RESULT);

        assertEquals(0, run(module, "request", request.toString()));
        assertEquals(0, run(module, "result", result.toString()));
        assertEquals(0, run(module, "pair", request.toString(), result.toString()));
    }

    private static int run(Path module, String command, String... files) throws IOException, InterruptedException {
        List<String> args = new java.util.ArrayList<>();
        args.add("python3");
        args.add("ssnvs_check.py");
        args.add(command);
        args.addAll(List.of(files));
        Process process = new ProcessBuilder(args).directory(module.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        assertEquals(0, exit, output);
        return exit;
    }

    private static boolean python3Available() {
        try {
            Process process = new ProcessBuilder("python3", "--version").redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
