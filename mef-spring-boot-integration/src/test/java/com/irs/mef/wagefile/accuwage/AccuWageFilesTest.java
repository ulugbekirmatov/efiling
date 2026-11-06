package com.irs.mef.wagefile.accuwage;

import com.irs.mef.wagefile.Efw2Validator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writes every AccuWage scenario file plus MANIFEST.tsv. Output goes to -Daccuwage.out, default
 * target/accuwage-ty&lt;year&gt;; generate-accuwage-files.sh points it at reports/accuwage-ty&lt;year&gt;.
 */
class AccuWageFilesTest {

    private static final Set<String> STRUCTURAL_DEFECTS = Set.of(
            "94-rt-wages-out-of-balance.txt",
            "95-rf-employee-count-wrong.txt",
            "96-missing-rf-record.txt",
            "97-record-511-bytes.txt");

    private static final Path OUTPUT = Path.of(
            System.getProperty("accuwage.out", "target/accuwage-ty" + ScenarioKit.TAX_YEAR));

    @Test
    void cleanScenariosPassTheStructuralValidator() {
        Map<String, List<String>> findings = new TreeMap<>();
        for (AccuWageScenario scenario : AccuWageScenarios.all()) {
            List<String> fileFindings = new Efw2Validator().validate(scenario.fileText());
            if (scenario.expected() == AccuWageScenario.AccuWageOutcome.NO_ERRORS && !fileFindings.isEmpty()) {
                findings.put(scenario.fileName(), fileFindings);
            }
        }
        assertEquals(Map.of(), findings);
    }

    @Test
    void everyNegativeFileDiffersFromItsCleanBase() {
        String base = NegativeScenarios.baseFile();
        assertEquals(List.of(), new Efw2Validator().validate(base));
        for (AccuWageScenario scenario : NegativeScenarios.scenarios()) {
            assertEquals(AccuWageScenario.AccuWageOutcome.ERRORS, scenario.expected(), scenario.fileName());
            assertNotEquals(base, scenario.fileText(), scenario.fileName() + " carries no defect");
        }
    }

    @Test
    void structuralDefectsFailTheJavaValidator() {
        for (AccuWageScenario scenario : NegativeScenarios.scenarios()) {
            if (STRUCTURAL_DEFECTS.contains(scenario.fileName())) {
                assertFalse(new Efw2Validator().validate(scenario.fileText()).isEmpty(), scenario.fileName());
            }
        }
    }

    @Test
    void scenariosAreNamedDescribedAndUnique() {
        Set<String> names = new HashSet<>();
        List<String> stubs = new ArrayList<>();
        for (AccuWageScenario scenario : AccuWageScenarios.all()) {
            assertTrue(names.add(scenario.fileName()), "duplicate " + scenario.fileName());
            if (scenario.purpose().startsWith("TODO") || scenario.expectedResult().startsWith("TODO")) {
                stubs.add(scenario.fileName());
            }
        }
        assertEquals(List.of(), stubs, "scenarios still stubbed");
    }

    @Test
    void writesEveryFileAndTheManifest() throws IOException {
        Files.createDirectories(OUTPUT);
        StringBuilder manifest = new StringBuilder("file\texpected\tpurpose\texpected_result\n");
        for (AccuWageScenario scenario : AccuWageScenarios.all()) {
            Files.write(OUTPUT.resolve(scenario.fileName()), scenario.fileText().getBytes(StandardCharsets.US_ASCII));
            manifest.append(scenario.fileName()).append('\t').append(scenario.expected()).append('\t')
                    .append(scenario.purpose()).append('\t').append(scenario.expectedResult()).append('\n');
        }
        Files.writeString(OUTPUT.resolve("MANIFEST.tsv"), manifest, StandardCharsets.US_ASCII);
        for (AccuWageScenario scenario : AccuWageScenarios.all()) {
            assertEquals(scenario.fileText(), Files.readString(OUTPUT.resolve(scenario.fileName()),
                    StandardCharsets.US_ASCII), scenario.fileName());
        }
    }
}
