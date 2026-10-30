package com.irs.mef.returnpdf;

import com.irs.mef.ats.AtsScenario;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleRSheetTest {

    private static final String PAGE1 = "topmostSubform[0].Page1[0].";
    private static final String PAGE2 = "topmostSubform[0].Page2[0].";

    @Test
    void daffodilValuesLandOnTheScheduleRFields() throws IOException {
        Map<String, String> values = scheduleR(sample());

        assertThat(values)
                .containsEntry(PAGE1 + "EINAndName[0].f1_1[0]", "00")
                .containsEntry(PAGE1 + "EINAndName[0].f1_2[0]", "3222220")
                .containsEntry(PAGE1 + "EINAndName[0].f1_3[0]", "Daffodil Accounting")
                .containsEntry(PAGE1 + "EINAndName[0].c1_1[1]", "2")
                .containsEntry(PAGE1 + "ReportForBox[0].c1_2[0]", "1")
                .containsEntry(PAGE1 + "ReportForBox[0].f1_4[0]", "2026")
                .containsEntry(PAGE1 + "Table_ColA-I[0].Row1[0].f1_5[0]", "003333336")
                .containsEntry(PAGE1 + "Table_ColA-I[0].Row1[0].f1_6[0]", "A")
                .containsEntry(PAGE1 + "Table_ColA-I[0].Row1[0].f1_7[0]", "5")
                .containsEntry(PAGE1 + "Table_ColA-I[0].Row1[0].f1_9[0]", "5,100")
                .containsEntry(PAGE1 + "Table_ColA-I[0].Row1[0].f1_10[0]", "00")
                .containsEntry(PAGE1 + "Table_ColA-I[0].Row1[0].f1_15[0]", "632")
                .containsEntry(PAGE1 + "Table_ColA-I[0].Row1[0].f1_16[0]", "40")
                .containsEntry(PAGE1 + "Table_ColJ-Q[0].Row1[0].f1_138[0]", "390")
                .containsEntry(PAGE1 + "Table_ColJ-Q[0].Row1[0].f1_139[0]", "15")
                .containsEntry(PAGE1 + "Line6[0].f1_84[0]", "5")
                .containsEntry(PAGE1 + "Line6[0].f1_85[0]", "5,100")
                .containsEntry(PAGE1 + "Line9[0].f1_123[0]", "10")
                .containsEntry(PAGE1 + "Line9[0].f1_130[0]", "1,264")
                .containsEntry(PAGE1 + "Line9[0].f1_131[0]", "80")
                .containsEntry(PAGE1 + "Table_ColR-Y[0].Row9[0].f1_401[0]", "2,760")
                .containsEntry(PAGE1 + "Table_ColR-Y[0].Row9[0].f1_402[0]", "60")
                .containsEntry(PAGE1 + "ThisScheduleBox[0].c1_3[0]", "1")
                .doesNotContainKey(PAGE1 + "Table_ColA-I[0].Row1[0].f1_13[0]")
                .doesNotContainKey(PAGE2 + "EINAndName[0].f2_3[0]");
    }

    @Test
    void aSixthClientContinuesOnPage2() throws IOException {
        Map<String, String> values = scheduleR(withExtraClients(sample(), 5));

        assertThat(values)
                .containsEntry(PAGE1 + "Table_ColA-I[0].Row5[0].f1_69[0]", "003333336")
                .containsEntry(PAGE2 + "Table_ColA-I[0].Row1[0].f2_7[0]", "003333336")
                .containsEntry(PAGE2 + "Table_ColA-I[0].Row1[0].f2_8[0]", "A")
                .containsEntry(PAGE2 + "Table_ColA-I[0].Row1[0].f2_9[0]", "5")
                .containsEntry(PAGE2 + "EINAndName[0].f2_3[0]", "00")
                .containsEntry(PAGE2 + "EINAndName[0].f2_4[0]", "3222220")
                .containsEntry(PAGE2 + "EINAndName[0].f2_5[0]", "Daffodil Accounting")
                .containsEntry(PAGE2 + "EINAndName[0].c2_1[1]", "2")
                .containsEntry(PAGE2 + "ReportForBox[0].f2_6[0]", "2026")
                .containsEntry(PAGE2 + "ReportForBox[0].c2_2[0]", "1")
                .containsEntry(PAGE2 + "ThisScheduleBox[0].c2_3[0]", "1");
    }

    @Test
    void moreClientsThanThePdfHoldsAreRejected() throws IOException {
        assertThatThrownBy(() -> ReturnPdf.fill(withExtraClients(sample(), 13)))
                .isInstanceOf(ReturnPdfException.class)
                .hasMessageContaining("14 clients");
    }

    @Test
    void oneClientLeavesTheContinuationSheetOut() throws IOException {
        byte[] pdf = ReturnPdf.render(sample());

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).as("941 (2), Schedule R (1), 8974 (1)").isEqualTo(4);
            assertThat(document.getDocumentCatalog().getAcroForm().getFields()).isEmpty();
            assertThat(new PDFTextStripper().getText(document))
                    .contains("Allocation Schedule for Aggregate Form 941 Filers")
                    .doesNotContain("Continuation Sheet for Schedule R")
                    .contains("Daffodil");
        }
    }

    @Test
    void aSixthClientPrintsTheContinuationSheet() throws IOException {
        byte[] pdf = ReturnPdf.render(withExtraClients(sample(), 5));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(document)).contains("Continuation Sheet for Schedule R");
        }
    }

    private static Map<String, String> scheduleR(String xml) {
        return ReturnPdf.fill(xml).stream()
                .filter(form -> form.form() == IrsForm.IRS941ScheduleR)
                .findFirst()
                .orElseThrow()
                .values();
    }

    private static String sample() throws IOException {
        return Files.readString(AtsScenario.DAFFODIL_941_SCHEDULE_R_8974.clientBody()
                .resolveSibling("composed-return.xml"));
    }

    private static String withExtraClients(String xml, int extras) {
        String closing = "</ClientInformationGrp>";
        int start = xml.indexOf("<ClientInformationGrp>");
        String block = xml.substring(start, xml.indexOf(closing) + closing.length());
        return xml.replace(closing, closing + block.repeat(extras));
    }
}
