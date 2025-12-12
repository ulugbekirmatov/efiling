# IRS MeF Test Scenarios

This directory contains test scenarios for IRS Modernized e-File (MeF) submissions. Each scenario includes:
- Source PDF test scenario document
- Generated Return XML file
- Scenario configuration (YAML)
- Documentation

## Directory Structure

```
test-scenarios/
├── README.md                          # This file
├── schemas/                           # XSD schema files organized by form
│   └── 941/                          # Form 941 schemas
│       ├── Return941.xsd
│       ├── ReturnHeader94x.xsd
│       ├── ReturnData941.xsd
│       ├── IRS941.xsd
│       └── efileTypes.xsd
└── 941-scenario-1-orchid-q1-2026/    # Test scenario folder
    ├── 941-test-scenario-1-ty2026.pdf # Source PDF
    ├── Return941-Scenario1.xml        # Generated XML
    └── scenario-description.md         # Scenario documentation
```

## Available Test Scenarios

### Form 941 - Quarterly Federal Tax Return

#### Scenario 1: Orchid Incorporated Q1 2026
- **Path**: `941-scenario-1-orchid-q1-2026/`
- **Form**: 941
- **Tax Year**: 2026 Q1
- **Description**: Basic quarterly tax return with 3 employees
- **Key Features**:
  - Simple payroll with social security and Medicare taxes
  - Total tax liability less than $2,500
  - No adjustments or special circumstances
  - Deposits match tax liability exactly
- **Status**: Ready for ATS testing

## Validating XML Files Locally

### Using JUnit Tests

The Spring Boot project includes automated validation tests:

```bash
cd mef-spring-boot-integration

# Run Form 941 XML validation tests
mvn test -Dtest=Form941XmlGenerationTest

# Run all scenario tests
mvn test -Dtest=*XmlGenerationTest
```

### Using xmllint (Command Line)

If you have xmllint installed:

```bash
cd test-scenarios/schemas/941
xmllint --noout --schema Return941.xsd ../../941-scenario-1-orchid-q1-2026/Return941-Scenario1.xml
```

## Creating New Test Scenarios

### Step 1: Create Scenario Folder

```bash
mkdir -p test-scenarios/{form}-scenario-{number}-{name}/
```

### Step 2: Add Source PDF

Place the IRS test scenario PDF in the folder.

### Step 3: Create Return XML

1. Copy an existing Return XML as a template
2. Update the ReturnHeader fields:
   - `ReturnTypeCd` - Form type (e.g., "941", "940")
   - `QuarterEndingDt` or tax period
   - Filer information (EIN, name, address)
   - Timestamps and IDs
3. Update the form-specific data section (e.g., `<IRS941>`)
4. Ensure all amounts have 2 decimal places

### Step 4: Create Configuration File

Create a YAML config file in the Spring Boot test resources:
- Path: `mef-spring-boot-integration/src/test/resources/test-scenarios/{scenario-name}/scenario-config.yml`
- Include all form data values for automated validation

### Step 5: Create Test Class

Copy and adapt an existing test class (e.g., `Form941XmlGenerationTest.java`) for the new scenario.

### Step 6: Validate

Run the test class to validate:
- XSD compliance
- Required fields present
- Values match source PDF
- Proper formatting

### Step 7: Document

Create a `scenario-description.md` file documenting:
- Source PDF information
- Business details
- Complete form data mapping
- Expected submission outcome

## Schema Management

Schemas are extracted from IRS MeF schema packages and organized by form type. Each form's schemas include:

- **Return{Form}.xsd** - Root return structure
- **ReturnHeader94x.xsd** - Header metadata (for employment tax forms)
- **ReturnData{Form}.xsd** - Data wrapper
- **IRS{Form}.xsd** - Form-specific fields
- **efileTypes.xsd** - Common type definitions

### Updating Schemas

When new schema versions are released:

1. Download the latest schema package from IRS
2. Extract schemas to temporary location
3. Copy to appropriate `schemas/{form}/` directory
4. Update `returnVersion` attribute in XML files
5. Re-run validation tests

## Integration with Spring Boot Application

The test scenarios integrate with the Spring Boot MeF application:

### Utilities

- **XmlValidator** - Validates XML against XSD schemas
- **ReturnXmlGenerator** - Generates dynamic values (timestamps, IDs)

### Tests

- **Form{XXX}XmlGenerationTest** - Validates XML structure and values
- **Form{XXX}SubmissionTest** - Integration tests (requires MeF credentials)

### Running Integration Tests

Integration tests that actually submit to IRS ATS require:
- Valid ETIN and credentials
- IRS-approved certificates
- Enable with system property: `-Dmef.integration.test.enabled=true`

```bash
mvn test -Dtest=Form941SubmissionTest -Dmef.integration.test.enabled=true
```

## Best Practices

1. **Version Control**: Commit XML files and configurations to version control
2. **Validation First**: Always validate XML before attempting IRS submission
3. **Schema Compliance**: Keep schemas up-to-date with IRS releases
4. **Documentation**: Document each scenario thoroughly
5. **Test Data**: Use realistic but anonymized test data
6. **Formatting**: Maintain consistent XML formatting and indentation

## Troubleshooting

### Common XSD Validation Errors

**Error**: `Element 'XXX' is not allowed`
- **Solution**: Check if element name matches schema exactly (case-sensitive)

**Error**: `Invalid value for element`
- **Solution**: Verify data types (string vs number) and format constraints

**Error**: `Required element missing`
- **Solution**: Add all required fields as specified in schema

### Amount Formatting Issues

All monetary amounts must:
- Have exactly 2 decimal places
- Use format: `1000.00` (not `1000.0` or `1000`)

### Namespace Issues

Ensure:
- Root element has `xmlns="http://www.irs.gov/efile"`
- Correct `returnVersion` attribute

## Resources

- **IRS MeF Documentation**: https://www.irs.gov/e-file-providers/modernized-e-file-mef-guide-for-software-developers
- **Schema Downloads**: https://www.irs.gov/e-file-providers/current-valid-xml-schemas-and-business-rules
- **Spring Boot Project**: `../mef-spring-boot-integration/`
- **Implementation Plan**: `../.claude/plans/curious-finding-clover.md`

## Support

For questions or issues:
1. Check the main project README: `../mef-spring-boot-integration/README.md`
2. Review IRS MeF documentation
3. Run validation tests for detailed error messages
4. Check schema files for field requirements

---

**Note**: These test scenarios are for ATS (Acceptance Testing System) only. Do not use production data or submit to production MeF servers without proper authorization.
