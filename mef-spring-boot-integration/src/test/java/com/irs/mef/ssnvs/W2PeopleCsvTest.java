package com.irs.mef.ssnvs;

import com.irs.mef.ssnvs.SsnvsInputException.Problem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static com.irs.mef.ssnvs.SsnvsFixtures.csv;
import static com.irs.mef.ssnvs.SsnvsFixtures.row;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class W2PeopleCsvTest {

    private static List<W2PersonRow> read(String text) {
        return W2PeopleCsv.read(text.getBytes(StandardCharsets.UTF_8));
    }

    private static SsnvsInputException failure(String text) {
        return assertThrows(SsnvsInputException.class, () -> read(text));
    }

    @Test
    @DisplayName("quoted fields keep commas and doubled quotes; blank cells are null")
    void quoting() {
        W2PersonRow row = read(csv(List.of(row(0).with("w2_form_ids", "11,12").with("company_codes", "C\"1")
                .with("hr_middle_name", null)))).get(0);

        assertEquals("11,12", row.w2FormIds());
        assertEquals("C\"1", row.companyCodes());
        assertNull(row.hrMiddleName());
        assertEquals(SsnvsFixtures.employeeId(0), row.employeeId());
        assertEquals(2, row.line());
    }

    @Test
    @DisplayName("a byte order mark, header case, extra columns, LF line ends and a timestamp date are tolerated")
    void tolerated() {
        String text = csv(List.of(row(0).with("hr_birth_dt", "1961-02-03 00:00:00")));
        String[] lines = text.split("\r\n");
        String widened = "\uFEFF" + lines[0].toUpperCase(Locale.ROOT) + ",extra\n" + lines[1] + ",x\n\n";

        W2PersonRow row = read(widened).get(0);

        assertEquals(LocalDate.of(1961, 2, 3), row.hrBirthDate());
        assertEquals(Boolean.TRUE, row.legalCompanyIsActive());
    }

    @Test
    @DisplayName("a missing contract column is an input error naming the column")
    void missingColumn() {
        String text = csv(List.of(row(0))).replace("w2_box_e_last_name", "box_e_last");

        SsnvsInputException e = failure(text);

        assertEquals(Problem.MISSING_COLUMN, e.problem());
        assertEquals("w2_box_e_last_name", e.column());
        assertEquals(1, e.line());
    }

    @Test
    @DisplayName("bad cells and broken quoting name the line and column, never the value")
    void badInput() {
        SsnvsInputException badDate = failure(csv(List.of(row(0), row(1).with("adp_birth_dt", "13/01/1970"))));
        assertEquals(List.of(Problem.BAD_VALUE, 3, "adp_birth_dt"), List.of(badDate.problem(), badDate.line(), badDate.column()));
        assertFalse(badDate.getMessage().contains("1970"));

        assertEquals(Problem.BAD_VALUE, failure(csv(List.of(row(0).with("employee_id", "0")))).problem());
        assertEquals(Problem.BAD_VALUE, failure(csv(List.of(row(0).with("legal_company_is_active", "maybe")))).problem());
        assertEquals(Problem.BAD_CSV_QUOTING, failure(csv(List.of(row(0))) + "\"unterminated").problem());
        assertEquals(Problem.BAD_CSV_QUOTING, failure(csv(List.of(row(0))).replace(",C1,", ",\"C1\"x,")).problem());
        assertEquals(Problem.BAD_ROW_WIDTH, failure(csv(List.of(row(0))) + "1,2\r\n").problem());
        assertEquals(Problem.NO_ROWS, failure(csv(List.of())).problem());
        assertEquals(Problem.MISSING_COLUMN, failure("").problem());
        assertEquals(Problem.MIXED_TAX_YEARS, failure(csv(List.of(row(0), row(1).with("tax_year", "2025")))).problem());
    }

    @Test
    @DisplayName("two employer EIN digit strings are MIXED_EMPLOYERS; the same digits with punctuation are one employer")
    void mixedEmployers() {
        SsnvsInputException mixed = failure(csv(List.of(row(0), row(1).with("employer_ein", SsnvsFixtures.ein(2)))));

        assertEquals(Problem.MIXED_EMPLOYERS, mixed.problem());
        assertEquals(3, mixed.line());
        assertEquals("employer_ein", mixed.column());
        assertEquals(2, read(csv(List.of(row(0), row(1).with("employer_ein", "98-0000001")))).size());
    }

    @Test
    @DisplayName("a quoted field may span lines; the next record keeps its own line number")
    void multilineField() {
        List<W2PersonRow> rows = read(csv(List.of(row(0).with("company_codes", "C1\nC2"), row(1))));

        assertEquals("C1\nC2", rows.get(0).companyCodes());
        assertEquals(4, rows.get(1).line());
    }
}
