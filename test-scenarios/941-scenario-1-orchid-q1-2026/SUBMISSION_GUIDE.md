# Form 941 Submission Guide

This guide explains how to submit the generated Form 941 XML to IRS MeF ATS (test environment).

## Prerequisites

### 1. IRS MeF Credentials

You need valid IRS MeF test credentials:
- **ETIN** (Electronic Transmitter Identification Number)
- **ASID** (Application System ID)
- **Username** and **Password** (if using basic auth)
- **Client Certificate** (X.509 certificate approved by IRS)

### 2. Configuration

Configure credentials in one of two ways:

#### Option A: Environment Variables (Recommended)
```bash
export MEF_ETIN=your_etin
export MEF_USERNAME=your_username
export MEF_PASSWORD=your_password
export MEF_ASID=your_asid
export MEF_KEYSTORE_PATH=/path/to/keystore.p12
export MEF_KEYSTORE_PASSWORD=your_password
export MEF_KEY_ALIAS=your_alias
```

#### Option B: application.yml
Edit `mef-spring-boot-integration/src/main/resources/application.yml`:
```yaml
mef:
  sdk:
    environment: ATS  # Must be ATS for testing
    authentication:
      etin: ${MEF_ETIN:your_etin}
      username: ${MEF_USERNAME:your_username}
      password: ${MEF_PASSWORD:your_password}
      asid: ${MEF_ASID:your_asid}
    certificate:
      keystore-path: ${MEF_KEYSTORE_PATH:/path/to/keystore.p12}
      keystore-password: ${MEF_KEYSTORE_PASSWORD:password}
      key-alias: ${MEF_KEY_ALIAS:alias}
```

### 3. Certificate Setup

The client certificate must be:
- In PKCS12 (.p12) or JKS format
- Signed by an IRS-approved Certificate Authority
- Valid and not expired
- Configured with correct alias and password

## Submission Process

### Step 1: Verify Configuration

```bash
cd mef-spring-boot-integration
mvn clean test -Dtest=Form941SubmissionTest#test01_verifyConfiguration
```

This verifies:
- ✓ Environment is set to ATS
- ✓ Certificate is configured
- ✓ Authentication credentials are configured
- ✓ Return941-Scenario1.xml exists

### Step 2: Run Full Submission Test

```bash
cd mef-spring-boot-integration
mvn test -Dtest=Form941SubmissionTest
```

This will:
1. **Verify configuration** - Check all prerequisites
2. **Login to IRS MeF ATS** - Authenticate and obtain SAML token
3. **Submit Form 941** - Send the XML to IRS
4. **Verify results** - Confirm submission was accepted

### Step 3: Check Results

If successful, you'll see output like:
```
✓ Submission successful!
  - Submission ID: 941_TEST_1234567890123
  - Deposit ID: <IRS_DEPOSIT_ID>
  - Status: Accepted
  - Message: Submission sent successfully to IRS MeF ATS
```

## What Gets Submitted

The test submits `Return941-Scenario1.xml` containing:

**Employer Information:**
- EIN: 00-3000004
- Business: Orchid Incorporated
- Location: Willow Grove, PA 19090

**Tax Period:**
- Q1 2026 (January - March)

**Form Data:**
- 3 employees
- $1,000.00 wages
- $100.00 federal tax withheld
- $124.00 Social Security tax
- $29.00 Medicare tax
- $253.00 total tax
- $253.00 deposits
- $0.00 balance due

## Expected Results

### Immediate Response
- **Deposit ID** - IRS tracking number for the submission
- **Status** - "Accepted" (submission received by IRS)

### After Processing (typically 5-30 minutes)
- **Acknowledgment** - IRS will generate an acknowledgment
- **Status Code** - Expected: "ACCEPT" (submission passed validation)

### Checking Status

After submission, you can check status using:
```bash
# Check submission status
curl -X GET "http://localhost:8080/api/mef/submissions/{submissionId}/status"

# Get new acknowledgments
curl -X GET "http://localhost:8080/api/mef/acknowledgments/new"
```

Note: These endpoints will need implementation. See `StatusService.java` and `AcknowledgementService.java`.

## Troubleshooting

### Authentication Errors

**Error:** "KEYSTORE_NOT_FOUND"
- **Solution:** Verify `MEF_KEYSTORE_PATH` points to existing file

**Error:** "Login failed - no SAML token returned"
- **Solution:** Check ETIN, username, password, and certificate validity

### Submission Errors

**Error:** "NOT_LOGGED_IN"
- **Solution:** Ensure login test passes before submission test

**Error:** "FILE_NOT_FOUND"
- **Solution:** Verify Return941-Scenario1.xml exists at correct path

**Error:** "SUBMISSION_FAILED"
- **Solution:** Check logs for detailed error from IRS. Common causes:
  - Invalid XML structure (should not happen - XML is pre-validated)
  - Certificate issues
  - Network connectivity
  - IRS ATS downtime

### Validation Errors

**Error:** XML schema validation failed
- **Solution:** Should not happen - XML is pre-validated. If it does:
  1. Re-run `Form941XmlGenerationTest` to verify XML
  2. Check IRS schema version matches (2026Q1v4.0)

## IRS Response Codes

Common acknowledgment status codes:

| Code | Meaning | Action |
|------|---------|--------|
| **ACCEPT** | Submission accepted | Success! No action needed |
| **RJCT** | Submission rejected | Review error details in acknowledgment |
| **RETRN** | Return to transmitter | Review and resubmit |

## Test Scenario Details

This submission uses **IRS ATS Test Scenario 1**:
- Source: `941-test-scenario-1-ty2026.pdf`
- Purpose: Basic 941 submission with minimal data
- Expected outcome: ACCEPT acknowledgment

## Next Steps After Submission

1. **Wait for processing** (5-30 minutes typical)
2. **Retrieve acknowledgments** using GetNewAcks API
3. **Verify ACCEPT status** in acknowledgment
4. **Archive submission and acknowledgment** per IRS requirements

## Production Considerations

Before submitting to production (PRD):
1. Change environment to `PRD` in application.yml
2. Use production credentials and certificate
3. Submit test returns to ATS first
4. Verify acknowledgment processing works
5. Implement error handling and retry logic
6. Implement acknowledgment polling and storage
7. Ensure compliance with IRS Publication 1345

## Additional Resources

- **IRS Publication 1345** - MeF Handbook
- **MeF Developer Portal** - https://www.irs.gov/e-file-providers
- **SDK Documentation** - `mef-spring-boot-integration/sdk-reference/docs/`
- **CLAUDE.md** - Project development guide

## Support

For issues:
1. Check logs: `mef-spring-boot-integration/logs/mef-spring-boot.log`
2. Review SDK logs: `a2a_sdk.log.*`
3. Consult IRS MeF technical support (for IRS-specific issues)
4. Review project documentation in CLAUDE.md
