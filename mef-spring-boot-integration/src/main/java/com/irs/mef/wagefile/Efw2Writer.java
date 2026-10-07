package com.irs.mef.wagefile;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Writes an EFW2 file: RA, then each employer's RE, RW rows, and RT, then RF. */
@Component
public final class Efw2Writer {

    public String write(WageYear year) {
        StringBuilder file = new StringBuilder();
        file.append(submitter(year.submitter())).append("\r\n");
        int totalEmployees = 0;
        for (EmployerWage employer : year.employers()) {
            file.append(employerRecord(employer)).append("\r\n");
            long[] totals = new long[Efw2Amount.values().length];
            for (EmployeeWage employee : employer.employees()) {
                file.append(employeeRecord(employee)).append("\r\n");
                for (Efw2Amount amount : Efw2Amount.values()) {
                    totals[amount.ordinal()] += employee.cents(amount);
                }
                totalEmployees++;
            }
            file.append(totalRecord(employer.employees().size(), totals)).append("\r\n");
        }
        file.append(finalRecord(totalEmployees)).append("\r\n");
        return file.toString();
    }

    /**
     * Copies a saved file and sets RA resubmit indicator (position 29) and the prior Wage File Identifier (30-35).
     * Totals are not rebuilt.
     */
    public byte[] markResubmit(byte[] file, String wageFileIdentifier) {
        String text = new String(file, StandardCharsets.US_ASCII);
        boolean trailingBreak = text.endsWith("\r\n");
        List<String> records = new ArrayList<>(Arrays.asList(text.split("\r\n", -1)));
        if (!records.isEmpty() && records.get(records.size() - 1).isEmpty()) {
            records.remove(records.size() - 1);
        }
        if (records.isEmpty()) {
            throw new IllegalArgumentException("wage file has no records");
        }
        FixedWidth submitter = FixedWidth.parse(records.get(0));
        submitter.put(29, 29, "1");
        submitter.put(30, 35, wageFileIdentifier);
        records.set(0, submitter.text());
        String patched = String.join("\r\n", records) + (trailingBreak ? "\r\n" : "");
        return patched.getBytes(StandardCharsets.US_ASCII);
    }

    private static String submitter(Submitter submitter) {
        FixedWidth record = new FixedWidth();
        record.put(1, 2, "RA");
        record.put(3, 11, submitter.ein());
        record.put(12, 19, FixedWidth.left(submitter.bsoUserId(), 8));
        record.put(29, 29, "0");
        record.put(30, 35, "      ");
        record.put(36, 37, "98");
        record.put(38, 94, FixedWidth.left(submitter.companyName(), 57));
        record.put(95, 116, FixedWidth.left(submitter.locationAddress(), 22));
        record.put(117, 138, FixedWidth.left(submitter.deliveryAddress(), 22));
        record.put(139, 160, FixedWidth.left(submitter.city(), 22));
        record.put(161, 162, submitter.state());
        record.put(163, 167, FixedWidth.left(submitter.zip(), 5));
        record.put(217, 273, FixedWidth.left(submitter.submitterName(), 57));
        record.put(446, 485, FixedWidth.leftRaw(submitter.contactEmail(), 40));
        record.put(500, 500, "S");
        return record.text();
    }

    private static String employerRecord(EmployerWage employer) {
        FixedWidth record = new FixedWidth();
        record.put(1, 2, "RE");
        record.put(3, 6, Integer.toString(employer.taxYear()));
        record.put(8, 16, employer.ein());
        record.put(26, 26, "0");
        record.put(40, 96, FixedWidth.left(employer.name(), 57));
        record.put(119, 140, FixedWidth.left(employer.deliveryAddress(), 22));
        record.put(141, 162, FixedWidth.left(employer.city(), 22));
        record.put(163, 164, employer.state());
        record.put(165, 169, FixedWidth.left(employer.zip(), 5));
        record.put(174, 174, "N");
        record.put(219, 219, "R");
        record.put(221, 221, "0");
        record.put(222, 248, FixedWidth.left(employer.contactName(), 27));
        record.put(249, 263, FixedWidth.leftRaw(employer.contactPhone(), 15));
        record.put(279, 318, FixedWidth.leftRaw(employer.contactEmail(), 40));
        return record.text();
    }

    private static String employeeRecord(EmployeeWage employee) {
        FixedWidth record = new FixedWidth();
        record.put(1, 2, "RW");
        record.put(3, 11, employee.ssn());
        record.put(12, 26, FixedWidth.left(employee.firstName(), 15));
        record.put(27, 41, FixedWidth.left(employee.middleName(), 15));
        record.put(42, 61, FixedWidth.left(employee.lastName(), 20));
        record.put(88, 109, FixedWidth.left(employee.deliveryAddress(), 22));
        record.put(110, 131, FixedWidth.left(employee.city(), 22));
        record.put(132, 133, employee.state());
        record.put(134, 138, FixedWidth.left(employee.zip(), 5));
        for (Efw2Amount amount : Efw2Amount.values()) {
            record.put(amount.rwStart(), amount.rwEnd(), FixedWidth.zeroFill(employee.cents(amount), 11));
        }
        record.put(486, 486, "0");
        record.put(488, 488, employee.retirementPlan() ? "1" : "0");
        record.put(489, 489, "0");
        return record.text();
    }

    private static String totalRecord(int employeeCount, long[] totals) {
        FixedWidth record = new FixedWidth();
        record.put(1, 2, "RT");
        record.put(3, 9, FixedWidth.zeroFill(employeeCount, 7));
        for (Efw2Amount amount : Efw2Amount.values()) {
            record.put(amount.rtStart(), amount.rtEnd(), FixedWidth.zeroFill(totals[amount.ordinal()], 15));
        }
        record.put(Efw2Amount.THIRD_PARTY_SICK_PAY_TAX_START, Efw2Amount.THIRD_PARTY_SICK_PAY_TAX_END,
                FixedWidth.zeroFill(0, 15));
        return record.text();
    }

    private static String finalRecord(int totalEmployees) {
        FixedWidth record = new FixedWidth();
        record.put(1, 2, "RF");
        record.put(8, 16, FixedWidth.zeroFill(totalEmployees, 9));
        return record.text();
    }
}
