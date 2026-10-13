package com.irs.mef.ssnvs;

import com.irs.mef.ssnvs.SsnvsInputException.Problem;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The CSV boundary. Reads a DBeaver export of sql/ssnvs_w2_people.sql (RFC 4180: comma, double quotes,
 * doubled quotes inside a quoted field, a header row) into {@link W2PersonRow}s. Columns are found by
 * name, extra columns are ignored, and a missing contract column is an input error naming that column.
 */
final class W2PeopleCsv {

    /** The SQL's output aliases. SqlCsvContractTest parses the SQL and asserts it selects exactly these. */
    static final List<String> CONTRACT_COLUMNS = List.of(
            "legal_company_id", "employer_ein", "legal_company_is_active", "tax_year", "employee_id",
            "active_w2_count", "w2_form_ids", "company_codes", "w2_box_b_ein", "ssn", "hr_ssn_digits",
            "w2_box_e_first_middle", "w2_box_e_last_name", "hr_first_name", "hr_middle_name",
            "hr_middle_initial", "hr_last_name", "adp_first_name", "adp_middle_name", "adp_last_name",
            "hr_birth_dt", "adp_birth_dt");

    private static final char BYTE_ORDER_MARK = '\uFEFF';
    private static final int HEADER_LINE = 1;

    private W2PeopleCsv() {
    }

    static List<W2PersonRow> read(byte[] csv) {
        List<CsvRecord> records = CsvRecord.parse(new String(csv, StandardCharsets.UTF_8));
        if (records.isEmpty()) {
            throw new SsnvsInputException(Problem.MISSING_COLUMN, HEADER_LINE, CONTRACT_COLUMNS.get(0));
        }
        Map<String, Integer> columnIndex = columnIndex(records.get(0));
        List<W2PersonRow> rows = new ArrayList<>();
        for (CsvRecord record : records.subList(1, records.size())) {
            if (record.fields().size() != records.get(0).fields().size()) {
                throw new SsnvsInputException(Problem.BAD_ROW_WIDTH, record.line(), null);
            }
            rows.add(new Cells(record, columnIndex).row());
        }
        if (rows.isEmpty()) {
            throw new SsnvsInputException(Problem.NO_ROWS, HEADER_LINE + 1, null);
        }
        String employerEin = SsnvsPlanner.digits(rows.get(0).employerEin());
        for (W2PersonRow row : rows) {
            if (row.taxYear() != rows.get(0).taxYear()) {
                throw new SsnvsInputException(Problem.MIXED_TAX_YEARS, row.line(), "tax_year");
            }
            if (!employerEin.equals(SsnvsPlanner.digits(row.employerEin()))) {
                throw new SsnvsInputException(Problem.MIXED_EMPLOYERS, row.line(), "employer_ein");
            }
        }
        return rows;
    }

    private static Map<String, Integer> columnIndex(CsvRecord header) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < header.fields().size(); i++) {
            index.putIfAbsent(header.fields().get(i).trim().toLowerCase(Locale.ROOT), i);
        }
        for (String column : CONTRACT_COLUMNS) {
            if (!index.containsKey(column)) {
                throw new SsnvsInputException(Problem.MISSING_COLUMN, header.line(), column);
            }
        }
        return index;
    }

    /** One record's cells, read by contract column name. Every parse failure names the column, not the value. */
    private record Cells(CsvRecord record, Map<String, Integer> columnIndex) {

        W2PersonRow row() {
            return new W2PersonRow(
                    record.line(),
                    text("legal_company_id"),
                    text("employer_ein"),
                    bool("legal_company_is_active"),
                    smallNumber("tax_year"),
                    employeeId(),
                    smallNumber("active_w2_count"),
                    text("w2_form_ids"),
                    text("company_codes"),
                    text("w2_box_b_ein"),
                    text("ssn"),
                    text("hr_ssn_digits"),
                    text("w2_box_e_first_middle"),
                    text("w2_box_e_last_name"),
                    text("hr_first_name"),
                    text("hr_middle_name"),
                    text("hr_middle_initial"),
                    text("hr_last_name"),
                    text("adp_first_name"),
                    text("adp_middle_name"),
                    text("adp_last_name"),
                    date("hr_birth_dt"),
                    date("adp_birth_dt"));
        }

        /** The employee id becomes the 14-digit control data, so it must fit there. */
        private long employeeId() {
            long employeeId = number("employee_id");
            if (employeeId < 1 || employeeId > ControlId.MAX) {
                throw bad("employee_id");
            }
            return employeeId;
        }

        private String text(String column) {
            String value = record.fields().get(columnIndex.get(column));
            return value.isBlank() ? null : value;
        }

        private long number(String column) {
            String value = text(column);
            if (value == null || !value.trim().matches("[0-9]{1,18}")) {
                throw bad(column);
            }
            return Long.parseLong(value.trim());
        }

        private int smallNumber(String column) {
            long value = number(column);
            if (value > Integer.MAX_VALUE) {
                throw bad(column);
            }
            return (int) value;
        }

        private Boolean bool(String column) {
            String value = text(column);
            if (value == null) {
                return null;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "true", "t", "1", "yes", "y" -> Boolean.TRUE;
                case "false", "f", "0", "no", "n" -> Boolean.FALSE;
                default -> throw bad(column);
            };
        }

        /** ISO date, optionally followed by a time part (DBeaver writes timestamps for date columns in some setups). */
        private LocalDate date(String column) {
            String value = text(column);
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            boolean timeFollows = trimmed.length() > 10 && (trimmed.charAt(10) == ' ' || trimmed.charAt(10) == 'T');
            if (trimmed.length() != 10 && !timeFollows) {
                throw bad(column);
            }
            try {
                return LocalDate.parse(trimmed.substring(0, 10));
            } catch (DateTimeParseException e) {
                throw bad(column);
            }
        }

        private SsnvsInputException bad(String column) {
            return new SsnvsInputException(Problem.BAD_VALUE, record.line(), column);
        }
    }

    /** One CSV record and the physical line it starts on. */
    private record CsvRecord(int line, List<String> fields) {

        static List<CsvRecord> parse(String text) {
            String body = !text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK ? text.substring(1) : text;
            return new Parser(body).records();
        }
    }

    private static final class Parser {
        private final String text;
        private final List<CsvRecord> records = new ArrayList<>();
        private List<String> fields = new ArrayList<>();
        private final StringBuilder field = new StringBuilder();
        private int line = 1;
        private int recordLine = 1;
        private boolean quoted;
        private boolean afterClosingQuote;

        Parser(String text) {
            this.text = text;
        }

        List<CsvRecord> records() {
            int i = 0;
            while (i < text.length()) {
                char c = text.charAt(i);
                if (quoted) {
                    i = inQuotes(c, i);
                } else {
                    i = outsideQuotes(c, i);
                }
                i++;
            }
            if (quoted) {
                throw new SsnvsInputException(Problem.BAD_CSV_QUOTING, recordLine, null);
            }
            if (!fields.isEmpty() || field.length() > 0 || afterClosingQuote) {
                endRecord();
            }
            return records;
        }

        private int inQuotes(char c, int i) {
            if (c == '"') {
                if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    return i + 1;
                }
                quoted = false;
                afterClosingQuote = true;
                return i;
            }
            if (c == '\n') {
                line++;
            }
            field.append(c);
            return i;
        }

        private int outsideQuotes(char c, int i) {
            switch (c) {
                case ',' -> endField();
                case '\r' -> {
                    endRecord();
                    line++;
                    recordLine = line;
                    return i + 1 < text.length() && text.charAt(i + 1) == '\n' ? i + 1 : i;
                }
                case '\n' -> {
                    endRecord();
                    line++;
                    recordLine = line;
                }
                case '"' -> {
                    if (field.length() > 0 || afterClosingQuote) {
                        throw new SsnvsInputException(Problem.BAD_CSV_QUOTING, line, null);
                    }
                    quoted = true;
                }
                default -> {
                    if (afterClosingQuote) {
                        throw new SsnvsInputException(Problem.BAD_CSV_QUOTING, line, null);
                    }
                    field.append(c);
                }
            }
            return i;
        }

        private void endField() {
            fields.add(field.toString());
            field.setLength(0);
            afterClosingQuote = false;
        }

        private void endRecord() {
            boolean blankLine = fields.isEmpty() && field.length() == 0 && !afterClosingQuote;
            endField();
            if (!blankLine) {
                records.add(new CsvRecord(recordLine, List.copyOf(fields)));
            }
            fields = new ArrayList<>();
        }
    }
}
