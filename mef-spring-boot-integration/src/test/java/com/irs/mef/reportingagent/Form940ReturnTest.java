package com.irs.mef.reportingagent;

import com.irs.mef.newsend.NewSendProperties;
import com.irs.mef.newsend.domain.FormType;
import com.irs.mef.newsend.domain.NewSendFiling;
import com.irs.mef.newsend.domain.NewSendManifestXml;
import com.irs.mef.newsend.domain.NewSendSubmissionId;
import com.irs.mef.newsend.domain.NewSendSubmitCommand;
import com.irs.mef.newsend.validate.XsdNewSendReturnXmlValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Offline. An annual 940 composes with TaxYr; until the 2026v2.0 schemas are bundled, validation stops it. */
class Form940ReturnTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-10-13T14:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    @Test
    void composesWithTaxYrInPlaceOfQuarterEndingDt() throws IOException {
        ClientReturnBody body = ClientReturnBody.load(Form940Body.write(dir));

        ReportingAgentReturn ret = ReportingAgentReturn.compose(
                ReportingAgentOriginatorStandIn.pyramosShaped(), body, FIXED);

        ReportingAgentReturn.Structure header = ret.structure();
        assertEquals("940", header.returnTypeCd());
        assertEquals("2026v2.0", header.returnVersion());
        assertEquals("TaxYr", header.periodElement());
        assertEquals("2026", header.periodValue());
        assertEquals("ReportingAgent", header.originatorTypeCd());
        assertTrue(ret.xml().contains("<TaxYr>2026</TaxYr>"), ret.xml());
        assertFalse(ret.xml().contains("QuarterEndingDt"), ret.xml());
    }

    @Test
    void manifestNamesForm940AndTheWholeYear() throws IOException {
        ReportingAgentReturn ret = ReportingAgentReturn.compose(
                ReportingAgentOriginatorStandIn.pyramosShaped(),
                ClientReturnBody.load(Form940Body.write(dir)), FIXED);

        NewSendSubmitCommand command = ret.toSubmitCommand("key", "client-940", false);
        String manifest = NewSendManifestXml.render(new NewSendFiling(
                new NewSendSubmissionId("0000002026286abc1234"), command, FIXED.instant()));

        assertEquals(FormType.F940, command.formType());
        assertEquals(LocalDate.of(2026, 1, 1), command.taxPeriod().begin());
        assertEquals(LocalDate.of(2026, 12, 31), command.taxPeriod().end());
        assertTrue(manifest.contains("<FederalSubmissionTypeCd>940</FederalSubmissionTypeCd>"), manifest);
        assertTrue(manifest.contains("<TaxPeriodBeginDt>2026-01-01</TaxPeriodBeginDt>"), manifest);
        assertTrue(manifest.contains("<TaxPeriodEndDt>2026-12-31</TaxPeriodEndDt>"), manifest);
    }

    @Test
    void validationStopsOnTheUnbundled2026v2Schemas() throws IOException {
        ReportingAgentReturn ret = ReportingAgentReturn.compose(
                ReportingAgentOriginatorStandIn.pyramosShaped(),
                ClientReturnBody.load(Form940Body.write(dir)), FIXED);
        NewSendProperties enforcing = new NewSendProperties();
        enforcing.setXmlValidation(NewSendProperties.XmlValidationMode.ENFORCE);
        XsdNewSendReturnXmlValidator validator = new XsdNewSendReturnXmlValidator(enforcing);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validator.validate(FormType.F940, ret.xml()));

        assertEquals("Bundled schema missing from classpath: schemas/94x/2026v2.0/EmploymentTax/940/Return940.xsd",
                ex.getMessage());
    }
}
