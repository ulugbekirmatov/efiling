package com.irs.mef.filing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Offline. The returnTypeCd and reporting-period guards, driven by edits of the scenario 1 body. */
class ClientReturnBodyTest {

    private static final String TYPE_941 = "returnTypeCd=\"941\"";

    @TempDir
    Path dir;

    @Test
    void scenario1BodyLoadsAs941() {
        ClientReturnBody body = ClientReturnBody.load(AtsScenario.ORCHID_941.clientBody());
        assertEquals(FormSpec.F941, body.form());
        assertEquals("003000004", body.filerEin().value());
    }

    @Test
    void rejects941XLabelOnA941ReturnData() throws IOException {
        Path mislabeled = edited(TYPE_941, "returnTypeCd=\"941X\"");
        ReportingAgentValidationException ex = assertThrows(
                ReportingAgentValidationException.class, () -> ClientReturnBody.load(mislabeled));
        assertEquals("returnTypeCd", ex.field());
        assertEquals("returnTypeCd 941X requires ReturnData to open with IRS941X, found IRS941", ex.getMessage());
    }

    @Test
    void rejectsReturnTypeOutsideTheRegistry() throws IOException {
        Path unsupported = edited(TYPE_941, "returnTypeCd=\"943\"");
        ReportingAgentValidationException ex = assertThrows(
                ReportingAgentValidationException.class, () -> ClientReturnBody.load(unsupported));
        assertEquals("returnTypeCd", ex.field());
        assertEquals("Unknown returnTypeCd '943'. Must be one of: 940, 941, 941X", ex.getMessage());
    }

    @Test
    void requiresReturnTypeCd() throws IOException {
        Path untyped = edited(TYPE_941, "");
        ReportingAgentValidationException ex = assertThrows(
                ReportingAgentValidationException.class, () -> ClientReturnBody.load(untyped));
        assertEquals("returnTypeCd", ex.field());
        assertEquals("returnTypeCd is required", ex.getMessage());
    }

    @Test
    void scenario1BodyCarriesItsQuarter() {
        ClientReturnBody body = ClientReturnBody.load(AtsScenario.ORCHID_941.clientBody());

        assertEquals(new ReportingPeriod.Quarter(YearMonth.of(2026, 3)), body.period());
        assertEquals("2026Q1v4.0", body.returnVersion());
    }

    @Test
    void rejectsTaxYrOnA941() throws IOException {
        Path annualLabel = edited(TYPE_941, TYPE_941 + " taxYr=\"2026\"");

        ReportingAgentValidationException ex = assertThrows(
                ReportingAgentValidationException.class, () -> ClientReturnBody.load(annualLabel));

        assertEquals("taxYr", ex.field());
        assertEquals("returnTypeCd 941 takes quarterEndingDt, not taxYr", ex.getMessage());
    }

    @Test
    void rejectsAQuarterThatIsNotTheTaxPeriodEnd() throws IOException {
        Path secondQuarter = edited("quarterEndingDt=\"2026-03\"", "quarterEndingDt=\"2026-06\"");

        ReportingAgentValidationException ex = assertThrows(
                ReportingAgentValidationException.class, () -> ClientReturnBody.load(secondQuarter));

        assertEquals("quarterEndingDt", ex.field());
        assertEquals("quarterEndingDt 2026-06 must equal tax period end year-month 2026-03", ex.getMessage());
    }

    @Test
    void annual940BodyLoadsWithItsTaxYear() throws IOException {
        Path annual = Form940Body.write(dir);

        ClientReturnBody body = ClientReturnBody.load(annual);

        assertEquals(FormSpec.F940, body.form());
        assertEquals(new ReportingPeriod.TaxYear(Year.of(2026)), body.period());
        assertEquals("2026v2.0", body.returnVersion());
        assertEquals(LocalDate.of(2026, 1, 1), body.taxPeriod().begin());
        assertEquals(LocalDate.of(2026, 12, 31), body.taxPeriod().end());
    }

    @Test
    void rejectsQuarterEndingDtOnA940() throws IOException {
        Path quarterly = Form940Body.write(dir, "taxYr=\"2026\"", "quarterEndingDt=\"2026-12\"");

        ReportingAgentValidationException ex = assertThrows(
                ReportingAgentValidationException.class, () -> ClientReturnBody.load(quarterly));

        assertEquals("quarterEndingDt", ex.field());
        assertEquals("returnTypeCd 940 takes taxYr, not quarterEndingDt", ex.getMessage());
    }

    @Test
    void rejectsA940CarryingBothPeriodAttributes() throws IOException {
        Path both = Form940Body.write(dir, "taxYr=\"2026\"", "taxYr=\"2026\" quarterEndingDt=\"2026-12\"");

        ReportingAgentValidationException ex = assertThrows(
                ReportingAgentValidationException.class, () -> ClientReturnBody.load(both));

        assertEquals("quarterEndingDt", ex.field());
    }

    @Test
    void rejectsA940WhosePeriodIsNotTheWholeYear() throws IOException {
        Path firstQuarter = Form940Body.write(dir, "2026-12-31", "2026-03-31");

        ReportingAgentValidationException ex = assertThrows(
                ReportingAgentValidationException.class, () -> ClientReturnBody.load(firstQuarter));

        assertEquals("taxYr", ex.field());
        assertEquals("taxYr 2026 requires tax period 2026-01-01..2026-12-31, found 2026-01-01..2026-03-31",
                ex.getMessage());
    }

    @Test
    void rejectsA940WithoutTaxYr() throws IOException {
        Path untimed = Form940Body.write(dir, " taxYr=\"2026\"", "");

        ReportingAgentValidationException ex = assertThrows(
                ReportingAgentValidationException.class, () -> ClientReturnBody.load(untimed));

        assertEquals("taxYr", ex.field());
        assertEquals("taxYr is required", ex.getMessage());
    }

    @Test
    void rejectsTheRetiredClient941BodyRoot() throws IOException {
        Path retired = Files.writeString(dir.resolve("retired.xml"),
                scenario1().replace("ClientReturnBody", "Client941Body"));
        ReportingAgentValidationException ex = assertThrows(
                ReportingAgentValidationException.class, () -> ClientReturnBody.load(retired));
        assertEquals("xml", ex.field());
    }

    private Path edited(String from, String to) throws IOException {
        String source = scenario1();
        if (!source.contains(from)) {
            throw new IllegalStateException("scenario 1 fixture no longer contains " + from);
        }
        return Files.writeString(dir.resolve("edited.xml"), source.replace(from, to));
    }

    private static String scenario1() throws IOException {
        return Files.readString(AtsScenario.ORCHID_941.clientBody());
    }
}
