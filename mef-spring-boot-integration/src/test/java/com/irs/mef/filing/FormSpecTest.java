package com.irs.mef.filing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FormSpecTest {

    @Test
    void aTaxYearWithoutABundledSchemaIsRefusedNamingTheBundledYears() {
        ReportingAgentValidationException ex = assertThrows(ReportingAgentValidationException.class,
                () -> FormSpec.F941X.schemaFor(2028));

        assertEquals("No bundled 941X schema for tax year 2028. Bundled tax years: 2026, 2027", ex.getMessage());
        assertEquals("taxPeriodBegin", ex.field());
    }

    @Test
    void theSchemaReleaseFollowsTheTaxYear() {
        assertEquals("2027Q1v2.0", FormSpec.F941X.schemaFor(2027).release().returnVersion());
        assertEquals("2026Q1v4.0", FormSpec.F941X.schemaFor(2026).release().returnVersion());
        assertEquals("schemas/94x/2027Q1v2.0/EmploymentTax/941X/Return941X.xsd",
                FormSpec.F941X.schemaFor(2027).resource());
    }

    @Test
    void theAnnual940HasNoQuarterlyYear() {
        assertThrows(ReportingAgentValidationException.class, () -> FormSpec.F940.schemaFor(2027));
    }
}
