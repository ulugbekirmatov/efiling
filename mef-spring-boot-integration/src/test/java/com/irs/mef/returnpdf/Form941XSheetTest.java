package com.irs.mef.returnpdf;

import com.irs.mef.ats.AtsScenario;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Form941XSheetTest {

    private static final String PAGE1 = "topmostSubform[0].Page1[0].";
    private static final String PAGE2 = "topmostSubform[0].Page2[0].";
    private static final String PAGE3 = "topmostSubform[0].Page3[0].";
    private static final String PAGE5 = "topmostSubform[0].Page5[0].";

    private static final String EXPLANATION = "Correcting Form 941 for the quarter ending March 31, 2026: wages, tips, "
            + "and other compensation (line 2) increased by $1,500.00, federal income tax withheld (line 3) "
            + "increased by $150.00, and taxable social security wages (line 5a, Column 1) and taxable Medicare "
            + "wages and tips (line 5c, Column 1) each increased by $1,500.00, as corrected in Part 3, lines 6, 7, "
            + "8, and 12 of this Form 941-X.";

    @Test
    void orchidCorrectionLandsOnTheAtlasFields() throws IOException {
        Map<String, String> values = form941x(Form941SheetTest.composed(AtsScenario.ORCHID_941X));

        assertThat(values)
                .containsEntry(PAGE1 + "Entity[0].f1_1[0]", "00")
                .containsEntry(PAGE1 + "Entity[0].f1_2[0]", "3000004")
                .containsEntry(PAGE1 + "Entity[0].f1_3[0]", "Orchid Incorporated")
                .containsEntry(PAGE1 + "Entity[0].f1_5[0]", "1st Test St")
                .containsEntry(PAGE1 + "Entity[0].f1_6[0]", "Willow Grove")
                .containsEntry(PAGE1 + "Entity[0].f1_7[0]", "PA")
                .containsEntry(PAGE1 + "Entity[0].f1_8[0]", "19090")
                .containsEntry(PAGE1 + "SideBoxes[0].c1_1[0]", "1")
                .containsEntry(PAGE1 + "SideBoxes[0].c1_2[0]", "1")
                .containsEntry(PAGE1 + "SideBoxes[0].f1_12[0]", "2026")
                .containsEntry(PAGE1 + "SideBoxes[0].f1_13[0]", "05")
                .containsEntry(PAGE1 + "SideBoxes[0].f1_14[0]", "21")
                .containsEntry(PAGE1 + "SideBoxes[0].f1_15[0]", "2026")
                .containsEntry(PAGE1 + "c1_4[0]", "1")
                .containsEntry(PAGE1 + "c1_5[0]", "2")
                .containsEntry(PAGE1 + "c1_6[0]", "2")
                .containsEntry(PAGE2 + "f2_2[0]", "2,500")
                .containsEntry(PAGE2 + "f2_3[0]", "00")
                .containsEntry(PAGE2 + "f2_4[0]", "1,000")
                .containsEntry(PAGE2 + "f2_5[0]", "00")
                .containsEntry(PAGE2 + "f2_6[0]", "1,500")
                .containsEntry(PAGE2 + "f2_7[0]", "00")
                .containsEntry(PAGE2 + "f2_8[0]", "250")
                .containsEntry(PAGE2 + "f2_9[0]", "00")
                .containsEntry(PAGE2 + "f2_10[0]", "100")
                .containsEntry(PAGE2 + "f2_12[0]", "150")
                .containsEntry(PAGE2 + "f2_16[0]", "2,500")
                .containsEntry(PAGE2 + "f2_22[0]", "186")
                .containsEntry(PAGE2 + "f2_23[0]", "00")
                .containsEntry(PAGE2 + "f2_48[0]", "2,500")
                .containsEntry(PAGE2 + "f2_54[0]", "43")
                .containsEntry(PAGE2 + "f2_55[0]", "50")
                .containsEntry(PAGE2 + "Name_ReadOrder[0].f1_3[0]", "Orchid Incorporated")
                .containsEntry(PAGE2 + "EIN_ReadOrder[0].f1_1[0]", "00")
                .containsEntry(PAGE2 + "EIN_ReadOrder[0].f1_2[0]", "3000004")
                .containsEntry(PAGE2 + "f2_1[0]", "1")
                .containsEntry(PAGE2 + "f1_12[0]", "2026")
                .containsEntry(PAGE3 + "f3_9[0]", "379")
                .containsEntry(PAGE3 + "f3_10[0]", "50")
                .containsEntry(PAGE3 + "f3_51[0]", "379")
                .containsEntry(PAGE3 + "f3_52[0]", "50")
                .containsEntry(PAGE5 + "YouMustGiveUs[0].f5_1[0]", EXPLANATION);
    }

    @Test
    void line7CopiesItsDifferenceIntoColumn4SoLine23Foots() throws IOException {
        Map<String, String> values = form941x(Form941SheetTest.composed(AtsScenario.ORCHID_941X));

        assertThat(values)
                .containsEntry(PAGE2 + "f2_12[0]", "150")
                .containsEntry(PAGE2 + "f2_14[0]", "150")
                .containsEntry(PAGE2 + "f2_15[0]", "00");
    }

    @Test
    void orchidRendersTheFivePageReturn() throws IOException {
        byte[] pdf = ReturnPdf.render(Form941SheetTest.composed(AtsScenario.ORCHID_941X));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(5);
            assertThat(document.getDocumentCatalog().getAcroForm().getFields()).isEmpty();
            assertThat(new PDFTextStripper().getText(document))
                    .contains("Adjusted Employer’s QUARTERLY Federal Tax Return")
                    .contains("Orchid")
                    .contains("Correcting Form 941");
        }
    }

    @Test
    void anExplanationThatDoesNotFitIsNotTruncated() throws IOException {
        String xml = Form941SheetTest.composed(AtsScenario.ORCHID_941X)
                .replaceAll("(?s)<CorrectionsExplanationTxt>.*</CorrectionsExplanationTxt>",
                        "<CorrectionsExplanationTxt>" + "A".repeat(4000) + "</CorrectionsExplanationTxt>");

        assertThatThrownBy(() -> ReturnPdf.fill(xml))
                .isInstanceOf(ReturnPdfException.class)
                .hasMessageContaining("CorrectionsExplanationTxt");
    }

    @Test
    void theReportingAgentPinNeverReachesPaper() throws IOException {
        String xml = Form941SheetTest.composed(AtsScenario.ORCHID_941X);
        String pin = xml.substring(xml.indexOf("<PIN>") + "<PIN>".length(), xml.indexOf("</PIN>"));

        assertThat(form941x(xml)).doesNotContainValue(pin);
    }

    private static Map<String, String> form941x(String xml) {
        return ReturnPdf.fill(xml).stream()
                .filter(form -> form.form() == IrsForm.IRS941X)
                .findFirst()
                .orElseThrow()
                .values();
    }
}
