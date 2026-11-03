package com.irs.mef.wagefile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WageFileReconcilerTest {

    private final WageFileReconciler reconciler = new WageFileReconciler();

    @Test
    @DisplayName("file wages 1000.00 fail against a corrected quarter of 2500.00")
    void correctedQuarterDisagrees() {
        EmployerTotals file = new EmployerTotals("003000004", 100_000L, 25_000L, 250_000L, 250_000L);
        QuarterAmounts quarter = new QuarterAmounts("003000004", 250_000L, 25_000L, 250_000L, 250_000L, true);

        assertEquals(
                List.of("003000004 wages file 1000.00 quarters 2500.00"),
                reconciler.differences(List.of(file), List.of(quarter)));
    }

    @Test
    @DisplayName("file 2500.00 / 250.00 / 2500.00 / 2500.00 matches the corrected quarter")
    void correctedQuarterMatches() {
        EmployerTotals file = new EmployerTotals("003000004", 250_000L, 25_000L, 250_000L, 250_000L);
        QuarterAmounts quarter = new QuarterAmounts("003000004", 250_000L, 25_000L, 250_000L, 250_000L, true);

        assertEquals(List.of(), reconciler.differences(List.of(file), List.of(quarter)));
    }
}
