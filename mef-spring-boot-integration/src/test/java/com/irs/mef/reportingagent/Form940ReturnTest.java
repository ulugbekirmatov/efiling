package com.irs.mef.reportingagent;

import com.irs.mef.newsend.NewSendProperties;
import com.irs.mef.newsend.domain.FormType;
import com.irs.mef.newsend.domain.SchemaRelease;
import com.irs.mef.newsend.error.NewSendConfigurationException;
import com.irs.mef.newsend.validate.XsdNewSendReturnXmlValidator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Offline. Until the 2026v2.0 bundle is on the classpath, no validation mode lets a 940 through. */
class Form940ReturnTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-10-13T14:00:00Z"), ZoneOffset.UTC);

    @ParameterizedTest(name = "mode {0}")
    @EnumSource(NewSendProperties.XmlValidationMode.class)
    void everyValidationModeRefusesA940WhileItsBundleIsAbsent(NewSendProperties.XmlValidationMode mode) {
        assumeFalse(ClasspathSchemas.bundled(SchemaRelease.Y2026_V2), "2026v2.0 bundle is present");
        String returnXml = ReportingAgentReturn.compose(
                ReportingAgentOriginatorStandIn.pyramosShaped(),
                ClientReturnBody.load(AtsScenario.ZINNIA_940.clientBody()), FIXED).xml();
        NewSendProperties properties = new NewSendProperties();
        properties.setXmlValidation(mode);
        XsdNewSendReturnXmlValidator validator = new XsdNewSendReturnXmlValidator(properties);

        NewSendConfigurationException ex = assertThrows(NewSendConfigurationException.class,
                () -> validator.validate(FormType.F940, returnXml));

        assertEquals("Bundled schema missing from classpath: schemas/94x/2026v2.0/EmploymentTax/940/Return940.xsd",
                ex.getMessage());
    }
}
