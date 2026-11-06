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

class ScheduleBSheetTest {

    private static final String PAGE = "topmostSubform[0].Page1[0].";
    private static final String ENTITY = PAGE + "Header-Entity[0].Entity[0].";

    @Test
    void marigoldLiabilitiesLandOnTheAtlasCells() throws IOException {
        String xml = Form941SheetTest.composed(AtsScenario.MARIGOLD_941_SCHEDULE_B);

        Map<String, String> values = scheduleB(xml);

        assertThat(values)
                .containsEntry(ENTITY + "f1_01[0]", "0")
                .containsEntry(ENTITY + "f1_02[0]", "0")
                .containsEntry(ENTITY + "f1_03[0]", "3")
                .containsEntry(ENTITY + "f1_04[0]", "3")
                .containsEntry(ENTITY + "f1_05[0]", "3")
                .containsEntry(ENTITY + "f1_06[0]", "3")
                .containsEntry(ENTITY + "f1_07[0]", "3")
                .containsEntry(ENTITY + "f1_08[0]", "3")
                .containsEntry(ENTITY + "f1_09[0]", "0")
                .containsEntry(ENTITY + "f1_10[0]", "Marigold Corporation")
                .containsEntry(ENTITY + "f1_11[0]", "2")
                .containsEntry(ENTITY + "f1_12[0]", "0")
                .containsEntry(ENTITY + "f1_13[0]", "2")
                .containsEntry(ENTITY + "f1_14[0]", "6")
                .containsEntry(PAGE + "c1_1[0]", "1")
                .containsEntry(PAGE + "Day9-16_Month1[0].f1_35[0]", "1,452")
                .containsEntry(PAGE + "Day9-16_Month1[0].f1_36[0]", "64")
                .containsEntry(PAGE + "Day17-24_Month1[0].f1_53[0]", "1,452")
                .containsEntry(PAGE + "Day17-24_Month1[0].f1_54[0]", "65")
                .containsEntry(PAGE + "f1_77[0]", "2,905")
                .containsEntry(PAGE + "f1_78[0]", "29")
                .containsEntry(PAGE + "Day1-8_Month2[0].f1_79[0]", "1,452")
                .containsEntry(PAGE + "Day1-8_Month2[0].f1_80_[0]", "64")
                .containsEntry(PAGE + "Day17-24_Month2[0].f1_113[0]", "1,452")
                .containsEntry(PAGE + "Day17-24_Month2[0].f1_114[0]", "65")
                .containsEntry(PAGE + "f1_141[0]", "2,905")
                .containsEntry(PAGE + "f1_142[0]", "29")
                .containsEntry(PAGE + "Day9-16_Month3[0].f1_161[0]", "1,452")
                .containsEntry(PAGE + "Day9-16_Month3[0].f1_162[0]", "64")
                .containsEntry(PAGE + "Day17-24_Month3[0].f1_179[0]", "1,452")
                .containsEntry(PAGE + "Day17-24_Month3[0].f1_180[0]", "67")
                .containsEntry(PAGE + "f1_205[0]", "2,905")
                .containsEntry(PAGE + "f1_206[0]", "31")
                .containsEntry(PAGE + "f1_207[0]", "8,715")
                .containsEntry(PAGE + "f1_208[0]", "89");
        assertThat(values).doesNotContainKey(PAGE + "c1_1[1]");
    }

    @Test
    void marigoldRendersThe941AndOneScheduleBPage() throws IOException {
        byte[] pdf = ReturnPdf.render(Form941SheetTest.composed(AtsScenario.MARIGOLD_941_SCHEDULE_B));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(3);
            assertThat(document.getDocumentCatalog().getAcroForm().getFields()).isEmpty();
            assertThat(new PDFTextStripper().getText(document))
                    .contains("Marigold")
                    .contains("Schedule B (Form 941)");
        }
    }

    @Test
    void dayNumbersLandInTheAtlasCellNotTheRowPosition() {
        String xml = scheduleOnly("""
                <TaxLiabilityQtrMonthlyDetail>
                  <MonthOfQuarterCd>1</MonthOfQuarterCd>
                  <DailyTaxLiabilityDetail><DayNum>1</DayNum><TaxLiabilityAmt>1.11</TaxLiabilityAmt></DailyTaxLiabilityDetail>
                  <DailyTaxLiabilityDetail><DayNum>8</DayNum><TaxLiabilityAmt>8.88</TaxLiabilityAmt></DailyTaxLiabilityDetail>
                  <DailyTaxLiabilityDetail><DayNum>9</DayNum><TaxLiabilityAmt>9.09</TaxLiabilityAmt></DailyTaxLiabilityDetail>
                  <DailyTaxLiabilityDetail><DayNum>16</DayNum><TaxLiabilityAmt>16.16</TaxLiabilityAmt></DailyTaxLiabilityDetail>
                  <DailyTaxLiabilityDetail><DayNum>17</DayNum><TaxLiabilityAmt>17.17</TaxLiabilityAmt></DailyTaxLiabilityDetail>
                  <DailyTaxLiabilityDetail><DayNum>24</DayNum><TaxLiabilityAmt>24.24</TaxLiabilityAmt></DailyTaxLiabilityDetail>
                  <DailyTaxLiabilityDetail><DayNum>25</DayNum><TaxLiabilityAmt>25.25</TaxLiabilityAmt></DailyTaxLiabilityDetail>
                  <DailyTaxLiabilityDetail><DayNum>31</DayNum><TaxLiabilityAmt>31.31</TaxLiabilityAmt></DailyTaxLiabilityDetail>
                  <TotalTaxLiabilityAmt>133.21</TotalTaxLiabilityAmt>
                </TaxLiabilityQtrMonthlyDetail>
                <TaxLiabilityQtrMonthlyDetail>
                  <MonthOfQuarterCd>3</MonthOfQuarterCd>
                  <DailyTaxLiabilityDetail><DayNum>31</DayNum><TaxLiabilityAmt>93.31</TaxLiabilityAmt></DailyTaxLiabilityDetail>
                  <TotalTaxLiabilityAmt>93.31</TotalTaxLiabilityAmt>
                </TaxLiabilityQtrMonthlyDetail>
                <TotalQuarterTaxLiabilityAmt>226.52</TotalQuarterTaxLiabilityAmt>
                """);

        Map<String, String> values = scheduleB(xml);

        assertThat(values)
                .containsEntry(PAGE + "Day1-8_Month1[0].f1_15[0]", "1")
                .containsEntry(PAGE + "Day1-8_Month1[0].f1_16[0]", "11")
                .containsEntry(PAGE + "Day1-8_Month1[0].f1_29[0]", "8")
                .containsEntry(PAGE + "Day1-8_Month1[0].f1_30[0]", "88")
                .containsEntry(PAGE + "Day9-16_Month1[0].f1_31[0]", "9")
                .containsEntry(PAGE + "Day9-16_Month1[0].f1_32[0]", "09")
                .containsEntry(PAGE + "Day9-16_Month1[0].f1_45[0]", "16")
                .containsEntry(PAGE + "Day9-16_Month1[0].f1_46[0]", "16")
                .containsEntry(PAGE + "Day17-24_Month1[0].f1_47[0]", "17")
                .containsEntry(PAGE + "Day17-24_Month1[0].f1_48[0]", "17")
                .containsEntry(PAGE + "Day17-24_Month1[0].f1_61[0]", "24")
                .containsEntry(PAGE + "Day17-24_Month1[0].f1_62[0]", "24")
                .containsEntry(PAGE + "Day25-31_Month1[0].f1_63[0]", "25")
                .containsEntry(PAGE + "Day25-31_Month1[0].f1_64[0]", "25")
                .containsEntry(PAGE + "Day25-31_Month1[0].f1_75[0]", "31")
                .containsEntry(PAGE + "Day25-31_Month1[0].f1_76[0]", "31")
                .containsEntry(PAGE + "Day25-31_Month3[0].f1_203[0]", "93")
                .containsEntry(PAGE + "Day25-31_Month3[0].f1_204[0]", "31");
        assertThat(values)
                .doesNotContainKey(PAGE + "Day1-8_Month1[0].f1_17[0]")
                .doesNotContainKey(PAGE + "Day25-31_Month2[0].f1_139[0]");
    }

    @Test
    void aDayNumOutsideTheMonthThrows() {
        assertThatThrownBy(() -> ReturnPdf.fill(scheduleOnly(month("1", day("0", "1.00")))))
                .isInstanceOf(ReturnPdfException.class)
                .hasMessageContaining("DayNum 0 is outside 1..31");
        assertThatThrownBy(() -> ReturnPdf.fill(scheduleOnly(month("1", day("32", "1.00")))))
                .isInstanceOf(ReturnPdfException.class)
                .hasMessageContaining("DayNum 32 is outside 1..31");
    }

    @Test
    void aMonthOutsideTheQuarterThrows() {
        assertThatThrownBy(() -> ReturnPdf.fill(scheduleOnly(month("0", ""))))
                .isInstanceOf(ReturnPdfException.class)
                .hasMessageContaining("MonthOfQuarterCd 0 is outside 1..3");
        assertThatThrownBy(() -> ReturnPdf.fill(scheduleOnly(month("4", ""))))
                .isInstanceOf(ReturnPdfException.class)
                .hasMessageContaining("MonthOfQuarterCd 4 is outside 1..3");
    }

    @Test
    void moreRowsThanTheFormHoldsThrows() {
        assertThatThrownBy(() -> ReturnPdf.fill(scheduleOnly(
                month("1", "") + month("2", "") + month("3", "") + month("1", ""))))
                .isInstanceOf(ReturnPdfException.class)
                .hasMessageContaining("4 TaxLiabilityQtrMonthlyDetail rows");

        StringBuilder days = new StringBuilder();
        for (int i = 0; i < 32; i++) {
            days.append(day("1", "1.00"));
        }
        assertThatThrownBy(() -> ReturnPdf.fill(scheduleOnly(month("1", days.toString()))))
                .isInstanceOf(ReturnPdfException.class)
                .hasMessageContaining("32 DailyTaxLiabilityDetail rows");
    }

    private static Map<String, String> scheduleB(String xml) {
        return ReturnPdf.fill(xml).stream()
                .filter(form -> form.form() == IrsForm.IRS941ScheduleB)
                .findFirst()
                .orElseThrow()
                .values();
    }

    private static String scheduleOnly(String body) {
        return """
                <Return xmlns="http://www.irs.gov/efile" returnVersion="2026Q1v4.0">
                  <ReturnHeader>
                    <QuarterEndingDt>2026-03</QuarterEndingDt>
                    <Filer>
                      <EIN>003333330</EIN>
                      <BusinessName>
                        <BusinessNameLine1Txt>Boundary Days</BusinessNameLine1Txt>
                      </BusinessName>
                      <USAddress>
                        <AddressLine1Txt>2nd Test Street</AddressLine1Txt>
                        <CityNm>Philadelphia</CityNm>
                        <StateAbbreviationCd>PA</StateAbbreviationCd>
                        <ZIPCd>19040</ZIPCd>
                      </USAddress>
                    </Filer>
                  </ReturnHeader>
                  <ReturnData documentCnt="1">
                    <IRS941ScheduleB documentId="IRS941ScheduleB">
                %s
                    </IRS941ScheduleB>
                  </ReturnData>
                </Return>
                """.formatted(body);
    }

    private static String month(String code, String days) {
        return "<TaxLiabilityQtrMonthlyDetail><MonthOfQuarterCd>" + code
                + "</MonthOfQuarterCd>" + days
                + "<TotalTaxLiabilityAmt>1.00</TotalTaxLiabilityAmt></TaxLiabilityQtrMonthlyDetail>";
    }

    private static String day(String dayNum, String amount) {
        return "<DailyTaxLiabilityDetail><DayNum>" + dayNum
                + "</DayNum><TaxLiabilityAmt>" + amount + "</TaxLiabilityAmt></DailyTaxLiabilityDetail>";
    }
}
