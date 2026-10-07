package com.irs.mef.wagefile;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Checks record length, RA/RE/RW/RT/RF order, and that each RT money total equals its RW rows. */
@Component
public final class Efw2Validator {

    public List<String> validate(String file) {
        if (file == null || file.isEmpty()) {
            return List.of("file has no records");
        }
        List<String> records = records(file);
        if (records.isEmpty()) {
            return List.of("file has no records");
        }
        List<String> lengthFindings = lengthFindings(records);
        if (!lengthFindings.isEmpty()) {
            return lengthFindings;
        }
        return grammarAndTotals(records);
    }

    private static List<String> records(String file) {
        String[] parts = file.split("\r\n", -1);
        List<String> records = new ArrayList<>(Arrays.asList(parts));
        if (!records.isEmpty() && records.get(records.size() - 1).isEmpty()) {
            records.remove(records.size() - 1);
        }
        return records;
    }

    private static List<String> lengthFindings(List<String> records) {
        List<String> findings = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            int bytes = records.get(i).getBytes(StandardCharsets.UTF_8).length;
            if (bytes != FixedWidth.RECORD_LENGTH) {
                findings.add("record " + (i + 1) + " is " + bytes + " bytes");
            }
        }
        return List.copyOf(findings);
    }

    private static List<String> grammarAndTotals(List<String> records) {
        List<String> findings = new ArrayList<>();
        if (!id(records.get(0)).equals("RA")) {
            return List.of("record 1 id " + id(records.get(0)) + ", expected RA");
        }
        if (!id(records.get(records.size() - 1)).equals("RF")) {
            return List.of("record " + records.size() + " id " + id(records.get(records.size() - 1)) + ", expected RF");
        }
        int index = 1;
        int totalEmployees = 0;
        if (index >= records.size() - 1) {
            return List.of("file has no RE record");
        }
        while (index < records.size() - 1) {
            if (!id(records.get(index)).equals("RE")) {
                return List.of("record " + (index + 1) + " id " + id(records.get(index)) + ", expected RE");
            }
            String ein = FixedWidth.parse(records.get(index)).get(8, 16);
            index++;
            if (index >= records.size() - 1 || !id(records.get(index)).equals("RW")) {
                return List.of("RE " + ein + " has no RW record");
            }
            long[] sums = new long[Efw2Amount.values().length];
            int rwCount = 0;
            while (index < records.size() - 1 && id(records.get(index)).equals("RW")) {
                FixedWidth employee = FixedWidth.parse(records.get(index));
                for (Efw2Amount money : Efw2Amount.values()) {
                    sums[money.ordinal()] += cents(employee.get(money.rwStart(), money.rwEnd()),
                            "RE " + ein + " " + money.label() + " RW");
                }
                rwCount++;
                index++;
            }
            if (index >= records.size() || !id(records.get(index)).equals("RT")) {
                return List.of("RE " + ein + " expected RT");
            }
            FixedWidth total = FixedWidth.parse(records.get(index));
            long reportedCount = cents(total.get(3, 9), "RE " + ein + " RT count");
            if (reportedCount != rwCount) {
                findings.add("RE " + ein + " RT count " + reportedCount + ", RW records " + rwCount);
            }
            for (Efw2Amount money : Efw2Amount.values()) {
                long reported = cents(total.get(money.rtStart(), money.rtEnd()),
                        "RE " + ein + " " + money.label() + " RT");
                if (reported != sums[money.ordinal()]) {
                    findings.add("RE " + ein + " " + money.label() + " RT " + reported
                            + " cents, RW " + sums[money.ordinal()] + " cents");
                }
            }
            totalEmployees += rwCount;
            index++;
        }
        if (index != records.size() - 1) {
            findings.add("records remain between the last RT and RF");
        }
        long rfCount = cents(FixedWidth.parse(records.get(records.size() - 1)).get(8, 16), "RF count");
        if (rfCount != totalEmployees) {
            findings.add("RF count " + rfCount + ", RW records " + totalEmployees);
        }
        return List.copyOf(findings);
    }

    private static String id(String record) {
        return record.substring(0, 2);
    }

    private static long cents(String field, String label) {
        for (int i = 0; i < field.length(); i++) {
            char c = field.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException(label + " is not digits: [" + field + "]");
            }
        }
        return Long.parseLong(field);
    }
}
