package com.irs.mef.returnpdf;

import com.irs.mef.ats.AtsScenario;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleRGeometryTest {

    private static final List<Map.Entry<String, Character>> COLUMNS = List.of(
            Map.entry("EmployeeCnt", 'c'), Map.entry("WagesAmt", 'd'), Map.entry("FederalIncomeTaxWithheldAmt", 'e'),
            Map.entry("TotalSocialSecurityTaxTipAmt", 'g'), Map.entry("TaxOnMedicareWagesTipsAmt", 'h'),
            Map.entry("TotalSSMdcrTaxAmt", 'i'), Map.entry("TaxOnUnreportedTips3121qAmt", 'j'),
            Map.entry("PayrollTaxCreditAmt", 'k'), Map.entry("TotalCrQlfySLFMLWagesAmt", 'l'),
            Map.entry("TotalCrQlfySLFMLWagesAftrAmt", 'n'), Map.entry("TotalCOBRAPremiumSubsidyAmt", 'o'),
            Map.entry("COBRAPremiumSubsidyRcpntCnt", 'p'), Map.entry("TotalTaxAmt", 'q'),
            Map.entry("TotalTaxDepositAmt", 'r'), Map.entry("TotQHPExpnssSSQlfyPdSLFMLWgAmt", 'u'),
            Map.entry("TotBrgnAgrmtQlfySLWgsAftrAmt", 'w'), Map.entry("TotQHPExpnssQlfyLvWgsAftrAmt", 'x'),
            Map.entry("TotBrgnAgrmtQlfyFMLWgsAftrAmt", 'y'));
    private static final int CLIENTS = 13;
    private static final int LINE_6 = 90;
    private static final int LINE_8 = 98;
    private static final int LINE_9 = 99;
    private static final float SAME_ROW = 3f;

    private static List<Cell> cells;

    @BeforeAll
    static void placeEveryCell() throws IOException {
        Map<String, String> values = ReturnPdf.fill(syntheticReturn()).stream()
                .filter(form -> form.form() == IrsForm.IRS941ScheduleR)
                .findFirst().orElseThrow().values();
        cells = new ArrayList<>();
        try (InputStream in = ScheduleRGeometryTest.class.getClassLoader()
                .getResourceAsStream("irs-forms/2026/f941sr.pdf");
             PDDocument pdf = Loader.loadPDF(in.readAllBytes())) {
            for (Map.Entry<String, String> value : values.entrySet()) {
                decode(value.getValue()).ifPresent(code -> {
                    PDField field = pdf.getDocumentCatalog().getAcroForm().getField(value.getKey());
                    PDAnnotationWidget widget = field.getWidgets().get(0);
                    PDRectangle rect = widget.getRectangle();
                    cells.add(new Cell(code[0], (char) code[1], pdf.getPages().indexOf(widget.getPage()),
                            rect.getLowerLeftX(), rect.getLowerLeftY(), value.getKey()));
                });
            }
        }
    }

    @Test
    void everyEncodedValueIsPlaced() {
        int rows = CLIENTS + 3;

        assertThat(cells).hasSize(rows * COLUMNS.size());
    }

    @Test
    void aColumnIsOneVerticalLineOnEachPage() {
        Map<String, List<Cell>> byPageAndColumn = cells.stream()
                .collect(Collectors.groupingBy(cell -> cell.page() + ":" + cell.column()));

        byPageAndColumn.forEach((key, column) -> assertThat(column)
                .as("column %s", key)
                .extracting(Cell::x)
                .allSatisfy(x -> assertThat(x).isCloseTo(column.get(0).x(), org.assertj.core.data.Offset.offset(1f))));
    }

    @Test
    void rowsGoDownTheFormInDocumentOrder() {
        Map<String, List<Cell>> byPageAndColumn = cells.stream()
                .collect(Collectors.groupingBy(cell -> cell.page() + ":" + cell.column()));

        byPageAndColumn.forEach((key, column) -> {
            List<Cell> sorted = column.stream().sorted(Comparator.comparingInt(Cell::row)).toList();
            IntStream.range(1, sorted.size()).forEach(i -> assertThat(sorted.get(i).y())
                    .as("column %s: row %d sits below row %d", key, sorted.get(i).row(), sorted.get(i - 1).row())
                    .isLessThan(sorted.get(i - 1).y()));
        });
    }

    @Test
    void columnsReadLeftToRightWithinABand() {
        Map<String, List<Cell>> byBand = cells.stream()
                .collect(Collectors.groupingBy(cell -> cell.page() + ":" + cell.row() + ":" + Math.round(cell.y() / SAME_ROW)));

        byBand.forEach((key, band) -> {
            List<Cell> sorted = band.stream().sorted(Comparator.comparing(Cell::column)).toList();
            IntStream.range(1, sorted.size()).forEach(i -> assertThat(sorted.get(i).x())
                    .as("band %s: column %s right of %s", key, sorted.get(i).column(), sorted.get(i - 1).column())
                    .isGreaterThan(sorted.get(i - 1).x()));
        });
    }

    @Test
    void clientsSixToThirteenAreOnTheContinuationSheet() {
        assertThat(cells).allSatisfy(cell -> assertThat(cell.page())
                .as("row %d", cell.row())
                .isEqualTo(cell.row() > 5 && cell.row() <= CLIENTS ? 1 : 0));
    }

    private static java.util.Optional<int[]> decode(String printed) {
        String digits = printed.replace(",", "");
        if (!digits.matches("\\d{3,4}")) {
            return java.util.Optional.empty();
        }
        int row = Integer.parseInt(digits.substring(0, digits.length() - 2));
        int columnNumber = Integer.parseInt(digits.substring(digits.length() - 2));
        boolean known = (row >= 1 && row <= CLIENTS) || row == LINE_6 || row == LINE_8 || row == LINE_9;
        if (!known || columnNumber < 3 || columnNumber > 25) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new int[] {row, 'a' + columnNumber - 1});
    }

    private static String syntheticReturn() throws IOException {
        String xml = Files.readString(AtsScenario.DAFFODIL_941_SCHEDULE_R_8974.clientBody()
                .resolveSibling("composed-return.xml"));
        StringBuilder clients = new StringBuilder();
        for (int row = 1; row <= CLIENTS; row++) {
            clients.append("<ClientInformationGrp><EIN>").append(900_000_000 + row)
                    .append("</EIN><WagesTypeCd>A</WagesTypeCd><ClientAllocationInformationGrp>")
                    .append(allocation(row)).append("</ClientAllocationInformationGrp></ClientInformationGrp>");
        }
        xml = xml.replaceAll("(?s)<ClientInformationGrp>.*</ClientInformationGrp>", clients.toString());
        xml = xml.replaceAll("(?s)<SubtotalForClientGrp>.*?</SubtotalForClientGrp>",
                "<SubtotalForClientGrp>" + allocation(LINE_6) + "</SubtotalForClientGrp>");
        xml = xml.replaceAll("(?s)<AggregateEmployeeInfoGrp>.*?</AggregateEmployeeInfoGrp>",
                "<AggregateEmployeeInfoGrp>" + allocation(LINE_8) + "</AggregateEmployeeInfoGrp>");
        xml = xml.replaceAll("(?s)<TotalAggregateAndClientInfoGrp>.*?</TotalAggregateAndClientInfoGrp>",
                "<TotalAggregateAndClientInfoGrp>" + allocation(LINE_9) + "</TotalAggregateAndClientInfoGrp>");
        return xml;
    }

    private static String allocation(int row) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, Character> column : COLUMNS) {
            String code = row + String.format("%02d", column.getValue() - 'a' + 1);
            boolean count = column.getKey().endsWith("Cnt");
            out.append('<').append(column.getKey()).append('>')
                    .append(count ? code : code + "." + String.format("%02d", row % 100))
                    .append("</").append(column.getKey()).append('>');
        }
        return out.toString();
    }

    private record Cell(int row, char column, int page, float x, float y, String field) {}
}
