package com.irs.mef.ssnvs;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Synthetic W-2 people for tests. Every SSN, name and birth date is computed from a person number, so no
 * identifier is written in source. SSNs follow accuwage ScenarioKit.person(n): valid shape (area 200-599,
 * group 10-98, serial 1000-9999), distinct for every n below 9000 * 89. Name tokens start with ZQ.
 */
final class SsnvsFixtures {

    static final LocalDate TODAY = LocalDate.of(2027, 1, 15);
    static final int TAX_YEAR = 2026;
    static final int FIRST_EMPLOYEE_ID = 4100;
    private static final int EIN_BASE = 980_000_000;
    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private SsnvsFixtures() {
    }

    static String ein(int employer) {
        return Integer.toString(EIN_BASE + employer);
    }

    static String ssn(int n) {
        return String.format("%03d%02d%04d", 200 + (n / (9000 * 89)) % 400, 10 + (n / 9000) % 89, 1000 + n % 9000);
    }

    /** Three letters that differ for every n below 26^3. */
    static String tag(int n) {
        return "" + LETTERS.charAt(n / 676 % 26) + LETTERS.charAt(n / 26 % 26) + LETTERS.charAt(n % 26);
    }

    static String last(int n) {
        return "ZQL" + tag(n);
    }

    static String first(int n) {
        return "ZQF" + tag(n);
    }

    static String middle(int n) {
        return "ZQM" + tag(n);
    }

    static LocalDate birthDate(int n) {
        return LocalDate.of(1960 + n % 40, 1 + n % 12, 1 + n % 28);
    }

    static long employeeId(int n) {
        return FIRST_EMPLOYEE_ID + n;
    }

    /** One export row for person n under employer 1, every field consistent. {@link #with} changes one cell. */
    static final class Row {
        final Map<String, String> cells = new LinkedHashMap<>();

        Row(int n) {
            cells.put("legal_company_id", "7");
            cells.put("employer_ein", ein(1));
            cells.put("legal_company_is_active", "true");
            cells.put("tax_year", Integer.toString(TAX_YEAR));
            cells.put("employee_id", Long.toString(employeeId(n)));
            cells.put("active_w2_count", "1");
            cells.put("w2_form_ids", Integer.toString(50_000 + n));
            cells.put("company_codes", "C1");
            cells.put("w2_box_b_ein", ein(1));
            cells.put("ssn", ssn(n));
            cells.put("hr_ssn_digits", ssn(n));
            cells.put("w2_box_e_first_middle", first(n) + " " + middle(n));
            cells.put("w2_box_e_last_name", last(n));
            cells.put("hr_first_name", first(n));
            cells.put("hr_middle_name", middle(n));
            cells.put("hr_middle_initial", middle(n).substring(0, 1));
            cells.put("hr_last_name", last(n));
            cells.put("adp_first_name", first(n));
            cells.put("adp_middle_name", middle(n));
            cells.put("adp_last_name", last(n));
            cells.put("hr_birth_dt", birthDate(n).toString());
            cells.put("adp_birth_dt", birthDate(n).toString());
        }

        Row with(String column, String value) {
            if (!cells.containsKey(column)) {
                throw new IllegalArgumentException("not a contract column: " + column);
            }
            cells.put(column, value == null ? "" : value);
            return this;
        }
    }

    static Row row(int n) {
        return new Row(n);
    }

    static List<Row> people(int count) {
        List<Row> rows = new ArrayList<>();
        for (int n = 0; n < count; n++) {
            rows.add(row(n));
        }
        return rows;
    }

    static String csv(List<Row> rows) {
        StringBuilder text = new StringBuilder(String.join(",", W2PeopleCsv.CONTRACT_COLUMNS)).append("\r\n");
        for (Row row : rows) {
            text.append(W2PeopleCsv.CONTRACT_COLUMNS.stream().map(column -> quote(row.cells.get(column)))
                    .collect(Collectors.joining(","))).append("\r\n");
        }
        return text.toString();
    }

    static byte[] csvBytes(List<Row> rows) {
        return csv(rows).getBytes(StandardCharsets.UTF_8);
    }

    static List<W2PersonRow> parsed(List<Row> rows) {
        return W2PeopleCsv.read(csvBytes(rows));
    }

    static EmployerPlan plan(Row... rows) {
        return SsnvsPlanner.plan(parsed(List.of(rows)), TODAY);
    }

    private static String quote(String cell) {
        return cell.contains(",") || cell.contains("\"") || cell.contains("\n") ? "\"" + cell.replace("\"", "\"\"") + "\"" : cell;
    }
}
