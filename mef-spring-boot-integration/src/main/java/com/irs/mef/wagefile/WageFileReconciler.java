package com.irs.mef.wagefile;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Sums quarter amounts and compares them to the built file. Does not read a submission journal. */
@Component
public final class WageFileReconciler {

    public List<String> differences(List<EmployerTotals> fileTotals, List<QuarterAmounts> quarters) {
        Map<String, long[]> sums = new LinkedHashMap<>();
        for (QuarterAmounts quarter : quarters) {
            long[] sum = sums.computeIfAbsent(quarter.ein(), ein -> new long[4]);
            sum[0] += quarter.wagesCents();
            sum[1] += quarter.withholdingCents();
            sum[2] += quarter.socialSecurityWagesCents();
            sum[3] += quarter.medicareWagesCents();
        }
        List<String> findings = new ArrayList<>();
        for (EmployerTotals file : fileTotals) {
            long[] sum = sums.getOrDefault(file.ein(), new long[4]);
            compare(findings, file.ein(), "wages", file.wagesCents(), sum[0]);
            compare(findings, file.ein(), "withholding", file.withholdingCents(), sum[1]);
            compare(findings, file.ein(), "social security wages", file.socialSecurityWagesCents(), sum[2]);
            compare(findings, file.ein(), "medicare wages", file.medicareWagesCents(), sum[3]);
        }
        return List.copyOf(findings);
    }

    private static void compare(List<String> findings, String ein, String label, long fileCents, long quarterCents) {
        if (fileCents != quarterCents) {
            findings.add(ein + " " + label + " file " + dollars(fileCents) + " quarters " + dollars(quarterCents));
        }
    }

    private static String dollars(long cents) {
        return (cents / 100) + "." + String.format(Locale.ROOT, "%02d", cents % 100);
    }
}
