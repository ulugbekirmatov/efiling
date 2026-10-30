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

class Form941SheetTest {

    private static final String PAGE1 = "topmostSubform[0].Page1[0].";
    private static final String PAGE2 = "topmostSubform[0].Page2[0].";

    @Test
    void marigoldLinesLandInTheirBoxes() throws IOException {
        Map<String, String> values = form941(composed(AtsScenario.MARIGOLD_941_SCHEDULE_B));

        assertThat(values).containsEntry(PAGE1 + "Header[0].EntityArea[0].f1_1[0]", "00")
                .containsEntry(PAGE1 + "Header[0].EntityArea[0].f1_2[0]", "3333330")
                .containsEntry(PAGE1 + "Header[0].EntityArea[0].f1_3[0]", "Marigold Corporation")
                .containsEntry(PAGE1 + "Header[0].ReportForQuarter[0].c1_1[0]", "1")
                .containsEntry(PAGE1 + "f1_12[0]", "10")
                .containsEntry(PAGE1 + "f1_13[0]", "33,594")
                .containsEntry(PAGE1 + "f1_14[0]", "00")
                .containsEntry(PAGE1 + "f1_19[0]", "4,165")
                .containsEntry(PAGE1 + "f1_20[0]", "66")
                .containsEntry(PAGE1 + "f1_49[0]", "8,715")
                .containsEntry(PAGE1 + "f1_50[0]", "89")
                .containsEntry(PAGE1 + "f1_55[0]", "284")
                .containsEntry(PAGE1 + "f1_56[0]", "11")
                .containsEntry(PAGE1 + "c1_4[1]", "2")
                .containsEntry(PAGE2 + "c2_1[2]", "3")
                .containsEntry(PAGE2 + "c2_4[1]", "2");
    }

    @Test
    void zeroBalanceDueIsPrintedNotDropped() throws IOException {
        Map<String, String> values = form941(composed(AtsScenario.ORCHID_941));

        assertThat(values).containsKey(PAGE1 + "f1_53[0]");
    }

    @Test
    void orchidRendersTwoFlattenedPagesOfTheIrsForm() throws IOException {
        byte[] pdf = ReturnPdf.render(composed(AtsScenario.ORCHID_941));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(2);
            assertThat(document.getDocumentCatalog().getAcroForm().getFields()).isEmpty();
            assertThat(new PDFTextStripper().getText(document))
                    .contains("Employer’s QUARTERLY Federal Tax Return")
                    .contains("Orchid");
        }
    }

    @Test
    void aSentValueWithNoBoxFailsTheRender() throws IOException {
        String withExtra = composed(AtsScenario.ORCHID_941)
                .replace("<EmployeeCnt>", "<UnmappedTestAmt>1.00</UnmappedTestAmt><EmployeeCnt>");

        assertThatThrownBy(() -> ReturnPdf.fill(withExtra))
                .isInstanceOf(ReturnPdfException.class)
                .hasMessageContaining("IRS941/UnmappedTestAmt");
    }

    @Test
    void theReportingAgentPinNeverReachesPaper() throws IOException {
        String xml = composed(AtsScenario.ORCHID_941);
        String pin = xml.substring(xml.indexOf("<PIN>") + "<PIN>".length(), xml.indexOf("</PIN>"));

        assertThat(form941(xml)).doesNotContainValue(pin);
    }

    private static Map<String, String> form941(String xml) {
        return ReturnPdf.fill(xml).stream()
                .filter(form -> form.form() == IrsForm.IRS941)
                .findFirst()
                .orElseThrow()
                .values();
    }

    static String composed(AtsScenario scenario) throws IOException {
        return Files.readString(scenario.clientBody().resolveSibling("composed-return.xml"));
    }
}
