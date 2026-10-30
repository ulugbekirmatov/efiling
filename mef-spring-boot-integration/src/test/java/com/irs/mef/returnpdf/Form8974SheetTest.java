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

class Form8974SheetTest {

    private static final String PAGE1 = "topmostSubform[0].Page1[0].";
    private static final String ENTITY = PAGE1 + "EntityArea[0].";
    private static final String ROW1 = PAGE1 + "Table_Part1[0].Row1[0].";

    @Test
    void daffodilCreditLandsOnTheAtlasFields() throws IOException {
        Map<String, String> values = form8974(sample());

        assertThat(values)
                .containsEntry(ENTITY + "f1_1[0]", "00")
                .containsEntry(ENTITY + "f1_2[0]", "3333336")
                .containsEntry(ENTITY + "f1_3[0]", "Marty Azalea")
                .containsEntry(ENTITY + "c1_1[0]", "1")
                .containsEntry(ENTITY + "f1_4[0]", "2026")
                .containsEntry(PAGE1 + "c1_2[0]", "1")
                .containsEntry(ROW1 + "ColA[0].f1_5[0]", "12")
                .containsEntry(ROW1 + "ColA[0].f1_6[0]", "31")
                .containsEntry(ROW1 + "ColA[0].f1_7[0]", "2025")
                .containsEntry(ROW1 + "f1_8[0]", "1040")
                .containsEntry(ROW1 + "ColC[0].f1_9[0]", "04")
                .containsEntry(ROW1 + "ColC[0].f1_10[0]", "15")
                .containsEntry(ROW1 + "ColC[0].f1_11[0]", "2026")
                .containsEntry(ROW1 + "ColE[0].f1_13[0]", "600")
                .containsEntry(ROW1 + "ColE[0].f1_14[0]", "00")
                .containsEntry(ROW1 + "ColF[0].f1_15[0]", "99")
                .containsEntry(ROW1 + "ColF[0].f1_16[0]", "96")
                .containsEntry(ROW1 + "ColG[0].f1_17[0]", "500")
                .containsEntry(ROW1 + "ColG[0].f1_18[0]", "04")
                .containsEntry(PAGE1 + "f1_75[0]", "500")
                .containsEntry(PAGE1 + "f1_76[0]", "04")
                .containsEntry(PAGE1 + "f1_77[0]", "500")
                .containsEntry(PAGE1 + "f1_78[0]", "04")
                .containsEntry(PAGE1 + "f1_79[0]", "632")
                .containsEntry(PAGE1 + "f1_80[0]", "40")
                .containsEntry(PAGE1 + "f1_85[0]", "316")
                .containsEntry(PAGE1 + "f1_86[0]", "20")
                .containsEntry(PAGE1 + "f1_89[0]", "183")
                .containsEntry(PAGE1 + "f1_90[0]", "84")
                .containsEntry(PAGE1 + "f1_91[0]", "147")
                .containsEntry(PAGE1 + "f1_92[0]", "90")
                .containsEntry(PAGE1 + "f1_97[0]", "390")
                .containsEntry(PAGE1 + "f1_98[0]", "15")
                .doesNotContainValue("Daffodil Accounting")
                .doesNotContainKey(PAGE1 + "f1_81[0]")
                .doesNotContainKey(PAGE1 + "Line11_ReadOrder[0].c1_3[0]");
    }

    @Test
    void daffodilRendersFormsInReturnDataOrder() throws IOException {
        byte[] pdf = ReturnPdf.render(sample());

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).as("941 (2), Schedule R (1), 8974 (1)").isEqualTo(4);
            assertThat(document.getDocumentCatalog().getAcroForm().getFields()).isEmpty();
            assertThat(new PDFTextStripper().getText(document))
                    .contains("Qualified Small Business Payroll Tax Credit")
                    .contains("Marty Azalea");
        }
    }

    private static Map<String, String> form8974(String xml) {
        return ReturnPdf.fill(xml).stream()
                .filter(form -> form.form() == IrsForm.IRS8974)
                .findFirst()
                .orElseThrow()
                .values();
    }

    private static String sample() throws IOException {
        return Files.readString(AtsScenario.DAFFODIL_941_SCHEDULE_R_8974.clientBody()
                .resolveSibling("composed-return.xml"));
    }
}
