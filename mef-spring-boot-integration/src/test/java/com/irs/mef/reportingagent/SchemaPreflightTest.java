package com.irs.mef.reportingagent;

import com.irs.mef.newsend.domain.FormType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Offline. No Spring. No IRS session. */
class SchemaPreflightTest {

    @Test
    void f941PassesWhenItsSchemaIsOnTheClasspath() {
        FormType form = FormType.F941;

        assertDoesNotThrow(() -> SchemaPreflight.require(form));
    }

    @Test
    void f940FailsWhenItsSchemaBundleIsMissing() {
        FormType form = FormType.F940;

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> SchemaPreflight.require(form));

        assertEquals(
                "Bundled schema missing from classpath: schemas/94x/2026v2.0/EmploymentTax/940/Return940.xsd; no IRS session was opened",
                thrown.getMessage());
    }
}
