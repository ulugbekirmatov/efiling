package com.irs.mef.scenarios;

import com.irs.mef.xml.XmlValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for validating the Form 941 XML generation for Scenario 1.
 * Tests include XSD validation, required field presence, and value accuracy.
 */
@Slf4j
public class Form941XmlGenerationTest {

    private static File xmlFile;
    private static File[] xsdFiles;
    private static Document xmlDocument;

    @BeforeAll
    public static void setup() throws Exception {
        // Path to the XML file - go up to parent directory then to test-scenarios
        File projectDir = new File(System.getProperty("user.dir"));
        File parentDir = projectDir.getParentFile();
        xmlFile = new File(parentDir, "test-scenarios/941-scenario-1-orchid-q1-2026/Return941-Scenario1.xml");

        log.info("XML file path: {}", xmlFile.getAbsolutePath());
        assertTrue(xmlFile.exists(), "XML file should exist: " + xmlFile.getAbsolutePath());

        // Path to XSD schema files
        // Note: Return941.xsd includes all other required schemas via xsd:include directives
        // We only need to load the main schema file
        String schemasPath = "src/test/resources/schemas/941";
        File schemasDir = new File(System.getProperty("user.dir"), schemasPath);
        File mainSchemaFile = new File(schemasDir, "Return941.xsd");

        xsdFiles = new File[]{mainSchemaFile};

        // Verify schema file exists
        assertTrue(mainSchemaFile.exists(), "Main schema file should exist: " + mainSchemaFile.getName());
        log.info("Main schema file found: {}", mainSchemaFile.getName());

        // Verify other schema files exist (they are included by Return941.xsd)
        String[] includedSchemas = {"efileTypes.xsd", "ReturnHeader94x.xsd", "IRS941.xsd", "ReturnData941.xsd"};
        for (String schemaName : includedSchemas) {
            File schemaFile = new File(schemasDir, schemaName);
            assertTrue(schemaFile.exists(), "Included schema should exist: " + schemaName);
            log.info("Included schema found: {}", schemaName);
        }

        // Parse XML document for field validation tests
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        xmlDocument = builder.parse(xmlFile);

        log.info("Setup complete. Ready to run tests.");
    }

    @Test
    public void testValidateReturnXmlAgainstSchemas() {
        log.info("Running XSD validation test...");

        // Validate XML against schemas
        XmlValidator.ValidationResult result = XmlValidator.validate(xmlFile, xsdFiles);

        // Generate and log validation report
        String report = XmlValidator.generateReport(result);
        log.info(report);

        // Assert validation passed
        assertTrue(result.isValid(),
                "XML should pass XSD validation. Errors found: " + result.getErrorCount());
        assertEquals(0, result.getErrorCount(),
                "There should be zero validation errors");
    }

    @Test
    public void testRequiredReturnHeaderFieldsPresent() throws Exception {
        log.info("Testing required ReturnHeader fields...");

        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new IrsNamespaceContext());
        xpath.setNamespaceContext(new IrsNamespaceContext());

        // Required ReturnHeader fields (use namespace prefix)
        assertFieldPresent(xpath, "//irs:ReturnTypeCd", "ReturnTypeCd");
        assertFieldPresent(xpath, "//irs:ReturnTs", "ReturnTs");
        assertFieldPresent(xpath, "//irs:SoftwareId", "SoftwareId");
        assertFieldPresent(xpath, "//irs:MultSoftwarePackagesUsedInd", "MultSoftwarePackagesUsedInd");
        assertFieldPresent(xpath, "//irs:QuarterEndingDt", "QuarterEndingDt");

        // Filer information
        assertFieldPresent(xpath, "//irs:Filer/irs:EIN", "Filer EIN");
        assertFieldPresent(xpath, "//irs:Filer/irs:BusinessName/irs:BusinessNameLine1Txt", "Business Name");
        assertFieldPresent(xpath, "//irs:Filer/irs:BusinessNameControlTxt", "Business Name Control");
        assertFieldPresent(xpath, "//irs:Filer/irs:USAddress/irs:AddressLine1Txt", "Address Line 1");
        assertFieldPresent(xpath, "//irs:Filer/irs:USAddress/irs:CityNm", "City");
        assertFieldPresent(xpath, "//irs:Filer/irs:USAddress/irs:StateAbbreviationCd", "State");
        assertFieldPresent(xpath, "//irs:Filer/irs:USAddress/irs:ZIPCd", "ZIP Code");

        // Originator information
        assertFieldPresent(xpath, "//irs:OriginatorGrp/irs:EFIN", "EFIN");
        assertFieldPresent(xpath, "//irs:OriginatorGrp/irs:OriginatorTypeCd", "Originator Type");

        log.info("All required ReturnHeader fields are present");
    }

    @Test
    public void testRequiredIRS941FieldsPresent() throws Exception {
        log.info("Testing required IRS941 fields...");

        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new IrsNamespaceContext());

        // Part 1 fields from the PDF
        assertFieldPresent(xpath, "//irs:IRS941/irs:EmployeeCnt", "Employee Count");
        assertFieldPresent(xpath, "//irs:IRS941/irs:WagesAmt", "Wages Amount");
        assertFieldPresent(xpath, "//irs:IRS941/irs:FederalIncomeTaxWithheldAmt", "Federal Income Tax Withheld");

        // Social Security and Medicare
        assertFieldPresent(xpath, "//irs:IRS941/irs:SocialSecurityWageAndTaxGrp/irs:SocialSecurityTaxCashWagesAmt", "SS Wages");
        assertFieldPresent(xpath, "//irs:IRS941/irs:SocialSecurityWageAndTaxGrp/irs:SocialSecurityTaxAmt", "SS Tax");
        assertFieldPresent(xpath, "//irs:IRS941/irs:MedicareWageTipsAndTaxGrp/irs:TaxableMedicareWagesTipsAmt", "Medicare Wages");
        assertFieldPresent(xpath, "//irs:IRS941/irs:MedicareWageTipsAndTaxGrp/irs:TaxOnMedicareWagesTipsAmt", "Medicare Tax");
        assertFieldPresent(xpath, "//irs:IRS941/irs:TotalSSMdcrTaxAmt", "Total SS/Medicare Tax");

        // Tax calculation fields
        assertFieldPresent(xpath, "//irs:IRS941/irs:TotalTaxBeforeAdjustmentAmt", "Total Tax Before Adjustment");
        assertFieldPresent(xpath, "//irs:IRS941/irs:TotalTaxAfterAdjustmentAmt", "Total Tax After Adjustment");
        assertFieldPresent(xpath, "//irs:IRS941/irs:TotalTaxAmt", "Total Tax");
        assertFieldPresent(xpath, "//irs:IRS941/irs:TotalTaxDepositAmt", "Total Deposits");
        assertFieldPresent(xpath, "//irs:IRS941/irs:BalanceDueAmt", "Balance Due");

        // Part 2 - Deposit schedule
        assertFieldPresent(xpath, "//irs:IRS941/irs:TotalTaxLessThanLimitAmtInd", "Total Tax Less Than Limit Indicator");

        log.info("All required IRS941 fields are present");
    }

    @Test
    public void testFieldValuesMatchScenario() throws Exception {
        log.info("Testing field values match PDF scenario...");

        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new IrsNamespaceContext());

        // Test ReturnHeader values
        assertFieldValue(xpath, "//irs:ReturnTypeCd", "941", "Return Type");
        assertFieldValue(xpath, "//irs:QuarterEndingDt", "2026-03", "Quarter Ending Date");
        assertFieldValue(xpath, "//irs:Filer/irs:EIN", "003000004", "EIN");
        assertFieldValue(xpath, "//irs:Filer/irs:BusinessName/irs:BusinessNameLine1Txt", "Orchid Incorporated", "Business Name");
        assertFieldValue(xpath, "//irs:Filer/irs:BusinessNameControlTxt", "ORCH", "Business Name Control");
        assertFieldValue(xpath, "//irs:Filer/irs:USAddress/irs:CityNm", "Willow Grove", "City");
        assertFieldValue(xpath, "//irs:Filer/irs:USAddress/irs:StateAbbreviationCd", "PA", "State");
        assertFieldValue(xpath, "//irs:Filer/irs:USAddress/irs:ZIPCd", "19090", "ZIP Code");

        // Test IRS941 values from PDF
        assertFieldValue(xpath, "//irs:IRS941/irs:EmployeeCnt", "3", "Employee Count");
        assertFieldValue(xpath, "//irs:IRS941/irs:WagesAmt", "1000.00", "Wages");
        assertFieldValue(xpath, "//irs:IRS941/irs:FederalIncomeTaxWithheldAmt", "100.00", "Federal Tax Withheld");

        // Social Security
        assertFieldValue(xpath, "//irs:IRS941/irs:SocialSecurityWageAndTaxGrp/irs:SocialSecurityTaxCashWagesAmt",
                "1000.00", "SS Wages");
        assertFieldValue(xpath, "//irs:IRS941/irs:SocialSecurityWageAndTaxGrp/irs:SocialSecurityTaxAmt",
                "124.00", "SS Tax");

        // Medicare
        assertFieldValue(xpath, "//irs:IRS941/irs:MedicareWageTipsAndTaxGrp/irs:TaxableMedicareWagesTipsAmt",
                "1000.00", "Medicare Wages");
        assertFieldValue(xpath, "//irs:IRS941/irs:MedicareWageTipsAndTaxGrp/irs:TaxOnMedicareWagesTipsAmt",
                "29.00", "Medicare Tax");

        // Totals
        assertFieldValue(xpath, "//irs:IRS941/irs:TotalSSMdcrTaxAmt", "153.00", "Total SS/Medicare Tax");
        assertFieldValue(xpath, "//irs:IRS941/irs:TotalTaxBeforeAdjustmentAmt", "253.00", "Total Tax Before Adjustment");
        assertFieldValue(xpath, "//irs:IRS941/irs:TotalTaxAfterAdjustmentAmt", "253.00", "Total Tax After Adjustment");
        assertFieldValue(xpath, "//irs:IRS941/irs:TotalTaxAmt", "253.00", "Total Tax");
        assertFieldValue(xpath, "//irs:IRS941/irs:TotalTaxDepositAmt", "253.00", "Total Deposits");
        assertFieldValue(xpath, "//irs:IRS941/irs:BalanceDueAmt", "0.00", "Balance Due");

        // Checkbox indicator
        assertFieldValue(xpath, "//irs:IRS941/irs:TotalTaxLessThanLimitAmtInd", "X", "Tax Less Than Limit Indicator");

        log.info("All field values match the PDF scenario");
    }

    @Test
    public void testNamespaceAndVersion() throws Exception {
        log.info("Testing namespace and return version...");

        // Check root element namespace
        Node root = xmlDocument.getDocumentElement();
        assertEquals("http://www.irs.gov/efile", root.getNamespaceURI(),
                "Root element should have correct namespace");
        assertEquals("Return", root.getLocalName(),
                "Root element should be 'Return'");

        // Check return version attribute
        String returnVersion = root.getAttributes().getNamedItem("returnVersion").getNodeValue();
        assertEquals("2026Q1v4.0", returnVersion,
                "Return version should be 2026Q1v4.0");

        log.info("Namespace and version are correct");
    }

    @Test
    public void testAmountFormatting() throws Exception {
        log.info("Testing amount formatting (2 decimal places)...");

        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new IrsNamespaceContext());

        // Test that all amount fields have exactly 2 decimal places
        String[] amountFields = {
                "//irs:IRS941/irs:WagesAmt",
                "//irs:IRS941/irs:FederalIncomeTaxWithheldAmt",
                "//irs:IRS941/irs:SocialSecurityWageAndTaxGrp/irs:SocialSecurityTaxCashWagesAmt",
                "//irs:IRS941/irs:SocialSecurityWageAndTaxGrp/irs:SocialSecurityTaxAmt",
                "//irs:IRS941/irs:MedicareWageTipsAndTaxGrp/irs:TaxableMedicareWagesTipsAmt",
                "//irs:IRS941/irs:MedicareWageTipsAndTaxGrp/irs:TaxOnMedicareWagesTipsAmt",
                "//irs:IRS941/irs:TotalSSMdcrTaxAmt",
                "//irs:IRS941/irs:TotalTaxBeforeAdjustmentAmt",
                "//irs:IRS941/irs:TotalTaxAfterAdjustmentAmt",
                "//irs:IRS941/irs:TotalTaxAmt",
                "//irs:IRS941/irs:TotalTaxDepositAmt",
                "//irs:IRS941/irs:BalanceDueAmt"
        };

        for (String fieldPath : amountFields) {
            String value = (String) xpath.evaluate(fieldPath, xmlDocument, XPathConstants.STRING);
            assertTrue(value.matches("\\d+\\.\\d{2}"),
                    "Amount field " + fieldPath + " should have 2 decimal places. Found: " + value);
        }

        log.info("All amounts are correctly formatted with 2 decimal places");
    }

    /**
     * Helper method to assert a field is present in the XML.
     */
    private void assertFieldPresent(XPath xpath, String xpathExpr, String fieldName) throws Exception {
        Node node = (Node) xpath.evaluate(xpathExpr, xmlDocument, XPathConstants.NODE);
        assertNotNull(node, fieldName + " should be present in XML (XPath: " + xpathExpr + ")");
    }

    /**
     * Helper method to assert a field has a specific value.
     */
    private void assertFieldValue(XPath xpath, String xpathExpr, String expectedValue, String fieldName)
            throws Exception {
        String actualValue = (String) xpath.evaluate(xpathExpr, xmlDocument, XPathConstants.STRING);
        assertEquals(expectedValue, actualValue,
                fieldName + " should have value '" + expectedValue + "' (XPath: " + xpathExpr + ")");
    }

    /**
     * Namespace context for IRS efile namespace.
     * Maps 'irs' prefix to the IRS efile namespace URI.
     */
    private static class IrsNamespaceContext implements NamespaceContext {
        private static final String IRS_NAMESPACE_URI = "http://www.irs.gov/efile";

        @Override
        public String getNamespaceURI(String prefix) {
            if (prefix == null) {
                throw new IllegalArgumentException("Null prefix");
            }
            if ("irs".equals(prefix)) {
                return IRS_NAMESPACE_URI;
            } else if ("xml".equals(prefix)) {
                return XMLConstants.XML_NS_URI;
            }
            return XMLConstants.NULL_NS_URI;
        }

        @Override
        public String getPrefix(String uri) {
            if (IRS_NAMESPACE_URI.equals(uri)) {
                return "irs";
            }
            return null;
        }

        @Override
        public Iterator<String> getPrefixes(String uri) {
            return null;
        }
    }
}
