# Form 941 Test Scenario 1 - Orchid Incorporated Q1 2026

## Overview

**Scenario ID**: ATS Scenario 1
**Form**: 941 - Employer's Quarterly Federal Tax Return
**Tax Year**: 2026
**Quarter**: Q1 (January, February, March)
**Status**: Ready for ATS Testing
**Created**: December 3, 2025

## Source Document

**PDF File**: `941-test-scenario-1-ty2026.pdf`
**Date**: July 23, 2025
**Source**: IRS MeF ATS Test Scenarios

## Business Information

### Filer Details

- **Business Name**: Orchid Incorporated
- **EIN**: 00-3000004
- **Business Name Control**: ORCH
- **Address**: 1st Test Street, Willow Grove, PA 19090

### Contact Information

- **Signature Method**: Online Filer PIN
- **Responsible Party Current Indicator**: Yes
- **Third-Party Designee**: No

## Form 941 Data

### Part 1: Quarter Information

#### Line 1: Number of Employees
- **Count**: 3 employees
- **Pay Period**: March 12, 2026 (for Q1)

#### Line 2: Wages, Tips, and Other Compensation
- **Amount**: $1,000.00

#### Line 3: Federal Income Tax Withheld
- **Amount**: $100.00

#### Line 4: Subject to Social Security or Medicare Tax
- **Status**: Yes (checkbox not checked, meaning wages ARE subject)

#### Line 5a: Taxable Social Security Wages
- **Wages**: $1,000.00
- **Tax Rate**: 0.124 (12.4%)
- **Tax**: $124.00

#### Line 5b: Taxable Social Security Tips
- **Amount**: Not applicable
- **Tax**: $0.00

#### Line 5c: Taxable Medicare Wages & Tips
- **Wages**: $1,000.00
- **Tax Rate**: 0.029 (2.9%)
- **Tax**: $29.00

#### Line 5d: Additional Medicare Tax Withholding
- **Amount**: Not applicable
- **Tax**: $0.00

#### Line 5e: Total Social Security and Medicare Taxes
- **Amount**: $153.00
- **Calculation**: $124.00 + $29.00 = $153.00

#### Line 5f: Section 3121(q) Notice and Demand
- **Amount**: Not applicable

#### Line 6: Total Taxes Before Adjustments
- **Amount**: $253.00
- **Calculation**: Line 3 + Line 5e + Line 5f = $100.00 + $153.00 + $0.00 = $253.00

#### Lines 7-9: Current Quarter Adjustments
- **Line 7** (Fractions of cents): Not applicable
- **Line 8** (Sick pay): Not applicable
- **Line 9** (Tips and group-term life insurance): Not applicable

#### Line 10: Total Taxes After Adjustments
- **Amount**: $253.00
- **Calculation**: Same as Line 6 (no adjustments)

#### Line 11: Qualified Small Business Payroll Tax Credit
- **Amount**: Not applicable

#### Line 12: Total Taxes After Adjustments and Credits
- **Amount**: $253.00

#### Line 13: Total Deposits for This Quarter
- **Amount**: $253.00
- **Note**: Deposits exactly match tax liability

#### Line 14: Balance Due
- **Amount**: $0.00
- **Reason**: Line 12 equals Line 13

#### Line 15: Overpayment
- **Amount**: Not applicable (no overpayment)

### Part 2: Deposit Schedule and Tax Liability

#### Line 16: Deposit Schedule Selection
- **Option Selected**: First option checked
- **Description**: "Line 12 on this return is less than $2,500"
- **Implication**: No monthly or semiweekly deposit schedule required

### Part 3: Business Information

#### Line 17: Business Closed or Stopped Paying Wages
- **Status**: No (not checked)

#### Line 18: Seasonal Employer
- **Status**: No (not checked)

### Part 4: Third-Party Designee

- **Allow Third Party**: No
- **Designee Information**: Not provided

### Part 5: Signature

- **Signature Option**: Use the signature method applicable to you
- **Implementation**: OnlineFilerPIN (for electronic filing)

## XML Mapping

### ReturnHeader Elements

| Field | XPath | Value |
|-------|-------|-------|
| Return Type | `/Return/ReturnHeader/ReturnTypeCd` | 941 |
| Quarter Ending | `/Return/ReturnHeader/QuarterEndingDt` | 2026-03 |
| EIN | `/Return/ReturnHeader/Filer/EIN` | 003000004 |
| Business Name | `/Return/ReturnHeader/Filer/BusinessName/BusinessNameLine1Txt` | Orchid Incorporated |
| Business Name Control | `/Return/ReturnHeader/Filer/BusinessNameControlTxt` | ORCH |
| Address | `/Return/ReturnHeader/Filer/USAddress/AddressLine1Txt` | 1st Test Street |
| City | `/Return/ReturnHeader/Filer/USAddress/CityNm` | Willow Grove |
| State | `/Return/ReturnHeader/Filer/USAddress/StateAbbreviationCd` | PA |
| ZIP Code | `/Return/ReturnHeader/Filer/USAddress/ZIPCd` | 19090 |

### IRS941 Form Elements

| Line | XPath | Value |
|------|-------|-------|
| 1 | `/Return/ReturnData/IRS941/EmployeeCnt` | 3 |
| 2 | `/Return/ReturnData/IRS941/WagesAmt` | 1000.00 |
| 3 | `/Return/ReturnData/IRS941/FederalIncomeTaxWithheldAmt` | 100.00 |
| 5a Wages | `/Return/ReturnData/IRS941/SocialSecurityWageAndTaxGrp/SocialSecurityTaxCashWagesAmt` | 1000.00 |
| 5a Tax | `/Return/ReturnData/IRS941/SocialSecurityWageAndTaxGrp/SocialSecurityTaxAmt` | 124.00 |
| 5c Wages | `/Return/ReturnData/IRS941/MedicareWageTipsAndTaxGrp/TaxableMedicareWagesTipsAmt` | 1000.00 |
| 5c Tax | `/Return/ReturnData/IRS941/MedicareWageTipsAndTaxGrp/TaxOnMedicareWagesTipsAmt` | 29.00 |
| 5e | `/Return/ReturnData/IRS941/TotalSSMdcrTaxAmt` | 153.00 |
| 6 | `/Return/ReturnData/IRS941/TotalTaxBeforeAdjustmentAmt` | 253.00 |
| 10 | `/Return/ReturnData/IRS941/TotalTaxAfterAdjustmentAmt` | 253.00 |
| 12 | `/Return/ReturnData/IRS941/TotalTaxAmt` | 253.00 |
| 13 | `/Return/ReturnData/IRS941/TotalTaxDepositAmt` | 253.00 |
| 14 | `/Return/ReturnData/IRS941/BalanceDueAmt` | 0.00 |
| 16 | `/Return/ReturnData/IRS941/TotalTaxLessThanLimitAmtInd` | X |

## Expected Submission Results

### Validation

- **XSD Validation**: ✓ PASS
- **Business Rules**: ✓ PASS (expected)
- **Schema Version**: 2026Q1v4.0

### IRS ATS Submission

- **Expected Status**: Accepted
- **Expected Acknowledgment**: Accepted
- **Balance Due**: $0.00
- **Refund**: $0.00

## Test Purpose

This scenario tests:

1. **Basic Form 941 Structure**: Simple quarterly return with minimal complexity
2. **Social Security and Medicare Tax Calculation**: Standard payroll tax computation
3. **Small Depositor Status**: Tax liability under $2,500 threshold
4. **Zero Balance**: Deposits exactly match tax liability
5. **Online Filer Signature**: Electronic signature method
6. **Responsible Party Indicator**: Current responsible party information

## Key Features

- **Simple Scenario**: No adjustments, credits, or special circumstances
- **All Employees Under Wage Base**: Social security wages not exceeding annual limits
- **Perfect Deposits**: No balance due or overpayment
- **Small Business**: Only 3 employees
- **Clean Quarter**: No tips, sick pay, or group-term life insurance adjustments

## Notes

- This is a basic test scenario suitable for initial ATS testing
- All wages are subject to both social security and Medicare taxes
- No additional Medicare tax withholding (wages under $200,000 threshold)
- Deposit schedule not required due to low tax liability
- Not a seasonal employer

## Files

| File | Path | Description |
|------|------|-------------|
| Source PDF | `941-test-scenario-1-ty2026.pdf` | Original IRS test scenario |
| Return XML | `Return941-Scenario1.xml` | Generated MeF submission XML |
| Configuration | `../../mef-spring-boot-integration/src/test/resources/test-scenarios/941-scenario-1/scenario-config.yml` | YAML configuration |
| Test Class | `../../mef-spring-boot-integration/src/test/java/com/irs/mef/scenarios/Form941XmlGenerationTest.java` | Validation tests |

## Validation

### Run Automated Tests

```bash
cd ../../mef-spring-boot-integration
mvn test -Dtest=Form941XmlGenerationTest
```

### Expected Test Results

- ✓ XSD validation passes
- ✓ All required fields present
- ✓ All values match PDF scenario
- ✓ Namespace and version correct
- ✓ Amount formatting (2 decimal places)

## Related Scenarios

- **Next**: Form 941 with adjustments or credits
- **Next**: Form 941 with semiweekly deposit schedule
- **Next**: Form 941-X (amended return)
- **Related**: Form 940 annual FUTA return (when available)

---

**Status**: ✓ XML Generated | ✓ Tests Created | ⏳ Pending ATS Submission
**Last Updated**: December 3, 2025
