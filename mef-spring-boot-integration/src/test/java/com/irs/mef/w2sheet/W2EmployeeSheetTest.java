package com.irs.mef.w2sheet;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class W2EmployeeSheetTest {

    private static final int COPIES_ON_SHEET = 4;

    @Test
    void printsJordansCopiesOnOneLetterSheetBothSides() throws IOException {
        String json = sample();

        try (PDDocument pdf = Loader.loadPDF(W2EmployeeSheet.render(W2EmployeeSheet.parse(json)))) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(2);
            assertThat(pdf.getPage(0).getMediaBox().getWidth()).isEqualTo(PDRectangle.LETTER.getWidth());
            assertThat(pdf.getPage(0).getMediaBox().getHeight()).isEqualTo(PDRectangle.LETTER.getHeight());
        }
    }

    @Test
    void everyCopyCarriesEveryValue() throws IOException {
        String front = pageText(W2EmployeeSheet.render(W2EmployeeSheet.parse(sample())), 1);

        Stream.of("58,000.00", "6,120.00", "3,968.00", "928.00", "6,000.00", "7,200.00", "44.80",
                        "1,964.80", "2,393.60", "XXX-XX-4321", "00-0000000", "12345678", "PHILADELPHIA",
                        "Zinnia Company (SAMPLE)", "1200 Market St Ste 400", "Jordan A Rivera",
                        "815 Spruce St Apt 3", "PA SUI", "Wage and Tax Statement", "2026",
                        "Department of the Treasury", "Internal Revenue Service")
                .forEach(value -> assertThat(occurrences(front, value)).as(value).isGreaterThanOrEqualTo(COPIES_ON_SHEET));
        // Boxes 3, 5, 16 and 18 all hold 64,000.00 on each of the four copies.
        assertThat(occurrences(front, "64,000.00")).isEqualTo(4 * COPIES_ON_SHEET);
    }

    @Test
    void eachCopyHasItsOwnLabelAndCopyCHasTheNegligenceNote() throws IOException {
        String front = pageText(W2EmployeeSheet.render(W2EmployeeSheet.parse(sample())), 1);

        assertThat(occurrences(front, "Copy B—To Be Filed With Employee’s FEDERAL Tax Return.")).isEqualTo(1);
        assertThat(occurrences(front, "Copy C—For EMPLOYEE’S RECORDS.")).isEqualTo(1);
        assertThat(occurrences(front, "Copy 2—To Be Filed With Employee’s State, City, or Local Income Tax Return.")).isEqualTo(2);
        assertThat(occurrences(front, "negligence penalty")).isEqualTo(1);
    }

    @Test
    void theBackCarriesEveryWordOfTheIrsTextInOrder() throws IOException {
        String back = pageText(W2EmployeeSheet.render(W2EmployeeSheet.parse(sample())), 2)
                .replaceAll("Back of Copy [BC2]", " ")
                .replaceAll("\\s+", " ");
        EmployeeInstructions irs = EmployeeInstructions.forTaxYear(2026);

        int from = 0;
        for (EmployeeInstructions.Paragraph paragraph : Stream.concat(irs.notice().stream(), irs.instructions().stream()).toList()) {
            String expected = paragraph.isHeading() ? paragraph.heading()
                    : ((paragraph.lead() == null ? "" : paragraph.lead() + " ") + paragraph.text()).replaceAll("\\s+", " ");
            int at = back.indexOf(expected, from);
            assertThat(at).as(expected).isGreaterThanOrEqualTo(0);
            from = at + expected.length();
        }
    }

    @Test
    void refusesAnythingButTheLastFourSsnDigits() {
        String json = sample().replace("\"ssnLast4\": \"4321\"", "\"ssnLast4\": \"54321\"");

        assertThatThrownBy(() -> W2EmployeeSheet.parse(json))
                .isInstanceOf(W2SheetException.class)
                .hasMessageContaining("four digits");
    }

    @Test
    void refusesAnAddressItCannotPrintWhole() {
        String json = sample().replace("\"Philadelphia PA 19107\"]\n  },\n  \"employee\"",
                "\"Floor 4\", \"Philadelphia PA 19107\"]\n  },\n  \"employee\"");

        assertThatThrownBy(() -> W2EmployeeSheet.render(W2EmployeeSheet.parse(json)))
                .isInstanceOf(W2SheetException.class)
                .hasMessageContaining("box c holds 3 lines");
    }

    @Test
    void refusesASecondStateLine() {
        String json = sample().replace("\"tax\": 1964.80 } ]",
                "\"tax\": 1964.80 }, { \"state\": \"NJ\", \"stateId\": \"9\", \"wages\": 1.00, \"tax\": 0.10 } ]");

        assertThatThrownBy(() -> W2EmployeeSheet.render(W2EmployeeSheet.parse(json)))
                .isInstanceOf(W2SheetException.class)
                .hasMessageContaining("one state line");
    }

    private static String sample() {
        try (InputStream in = W2EmployeeSheetTest.class.getResourceAsStream("/w2sheet/jordan-rivera-2026.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String pageText(byte[] pdf, int page) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            return stripper.getText(document);
        }
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }
}
