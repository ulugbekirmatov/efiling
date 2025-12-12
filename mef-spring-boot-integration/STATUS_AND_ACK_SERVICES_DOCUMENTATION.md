# GetSubmissionStatus and GetNewAcks Services Documentation

**Document Version**: 1.0
**Last Updated**: 2025-12-08
**Status**: Production Ready

---

## Table of Contents

1. [Overview](#overview)
2. [GetSubmissionStatus Service](#getsubmissionstatus-service)
3. [GetNewAcks Service](#getnewacks-service)
4. [Implementation Guide](#implementation-guide)
5. [Testing](#testing)
6. [Troubleshooting](#troubleshooting)

---

## Overview

This document provides complete specifications for the **GetSubmissionStatus** and **GetNewAcks** services implemented in the MeF Spring Boot Integration project. Both services are fully functional and tested against IRS MeF ATS (Acceptance Testing System).

### Services Implemented

| Service | Purpose | Status | Implementation |
|---------|---------|--------|----------------|
| GetSubmissionStatus | Query status of a specific submission | ✅ Working | `StatusService.java:33-101` |
| GetNewAcks | Retrieve new acknowledgments | ✅ Working | `AcknowledgementService.java:98-188` |

---

## GetSubmissionStatus Service

### Purpose

Queries the IRS MeF system for the current processing status of a previously submitted tax return.

### Service Endpoint

- **URL**: `https://la.alt.www4.irs.gov/a2a/mef/mime/GetSubmissionStatus` (ATS)
- **Method**: SOAP 1.1 with MTOM attachments
- **Protocol**: HTTPS with mutual TLS authentication

### Request Parameters

#### SOAP Request Structure

```xml
<S:Envelope xmlns:S="http://schemas.xmlsoap.org/soap/envelope/">
  <S:Header>
    <!-- WS-Security header with SAML assertion -->
    <wsse:Security mustUnderstand="1">
      <wsu:Timestamp>
        <wsu:Created>2025-12-08T13:23:50Z</wsu:Created>
        <wsu:Expires>2025-12-08T13:28:50Z</wsu:Expires>
      </wsu:Timestamp>
      <wsse:UsernameToken>
        <wsse:Username>23868900</wsse:Username>
      </wsse:UsernameToken>
      <saml:Assertion>
        <!-- Full SAML assertion from login -->
      </saml:Assertion>
    </wsse:Security>

    <!-- MeF Header -->
    <MeFHeader xmlns="http://www.irs.gov/a2a/mef/MeFHeader.xsd">
      <MessageID>9766120253423e6ildw9</MessageID>
      <Action>http://localhost:9080/a2a/mef/mime/GetSubmissionStatus</Action>
      <MessageTs>2025-12-08T16:23:50+03:00</MessageTs>
      <ETIN>97661</ETIN>
      <SessionKeyCd>Y</SessionKeyCd>
      <TestCd>T</TestCd>
      <AppSysID>23868900</AppSysID>
      <WSDLVersionNum>10.9</WSDLVersionNum>
      <ClientSoftwareTxt>MeFA2AJavaToolkit2025v16.0</ClientSoftwareTxt>
    </MeFHeader>
  </S:Header>

  <S:Body>
    <ns2:GetSubmissionStatusRequest xmlns:ns2="http://www.irs.gov/a2a/mef/MeFTransmitterService.xsd">
      <ns2:SubmissionId>1234562025331test941</ns2:SubmissionId>
    </ns2:GetSubmissionStatusRequest>
  </S:Body>
</S:Envelope>
```

#### SDK Method Signature

```java
GetSubmissionStatusResult invoke(ServiceContext context, String submissionId)
    throws ToolkitException, ServiceException
```

#### Java Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `serviceContext` | `ServiceContext` | Yes | Session context with SAML assertion from login |
| `submissionId` | `String` | Yes | 20-character submission ID (format: `[0-9]{13}[a-z0-9]{7}`) |

### Response Structure

#### SOAP Response Envelope

```xml
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
  <soapenv:Header>
    <!-- Echo of MeF header with RelatesTo -->
    <ns1:MeFHeader xmlns:ns1="http://www.irs.gov/a2a/mef/MeFHeader.xsd">
      <ns1:MessageID>9766120253423e6ildw9R</ns1:MessageID>
      <ns1:RelatesTo>9766120253423e6ildw9</ns1:RelatesTo>
      <ns1:Action>http://localhost:9080/a2a/mef/mime/GetSubmissionStatus</ns1:Action>
      <!-- ... other header fields ... -->
    </ns1:MeFHeader>
  </soapenv:Header>

  <soapenv:Body>
    <GetSubmissionStatusResponse xmlns="http://www.irs.gov/a2a/mef/MeFTransmitterService.xsd">
      <!-- Empty body - actual data in attachment -->
    </GetSubmissionStatusResponse>
  </soapenv:Body>
</soapenv:Envelope>
```

#### MTOM Attachment (StatusRecordList XML)

The actual status data is returned as a ZIP attachment containing an XML file:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<StatusRecordList xmlns="http://www.irs.gov/efile" cnt="1">
  <StatusRecordGrp>
    <SubmissionId>1234562025331test941</SubmissionId>
    <SubmissionStatusTxt>Accepted to MeF</SubmissionStatusTxt>
    <SubmsnStatusAcknowledgementDt>2025-12-08</SubmsnStatusAcknowledgementDt>
    <DisclaimerTxt>This status record provides information about what step in the process the return has completed. It is not proof that the return was Accepted or Rejected. You must retrieve the Acknowledgement File and keep it with the return records to prove that the return was Accepted or Rejected.</DisclaimerTxt>
  </StatusRecordGrp>
</StatusRecordList>
```

#### SDK Result Object

```java
GetSubmissionStatusResult result = client.invoke(serviceContext, submissionId);

// Extract status records
StatusRecordList statusRecordList = result.getStatusRecordList();
List<StatusRecordGrp> statusRecords = statusRecordList.getStatusRecords();

// Access status information
for (StatusRecordGrp record : statusRecords) {
    String submissionId = record.getSubmissionId();
    String status = record.getSubmissionStatusTxt();
    XMLGregorianCalendar statusDate = record.getSubmsnStatusAcknowledgementDt();
    String disclaimer = record.getDisclaimerTxt();
}
```

### Response Fields

| Field | Type | Description |
|-------|------|-------------|
| `SubmissionId` | String | The submission ID that was queried |
| `SubmissionStatusTxt` | String | Current status (e.g., "Accepted to MeF", "Rejected") |
| `SubmsnStatusAcknowledgementDt` | XMLGregorianCalendar | Date/time when status was last updated |
| `DisclaimerTxt` | String | IRS disclaimer text about the status |

### Possible Status Values

- **"Accepted to MeF"** - Submission received by IRS MeF system
- **"Processing"** - Submission being processed
- **"Accepted"** - Return accepted by IRS
- **"Rejected"** - Return rejected by IRS (check acknowledgment for errors)
- **"Pending"** - Awaiting processing

### Example Usage

```java
@Autowired
private StatusService statusService;

// Query status
String submissionId = "1234562025331test941";
StatusResponse response = statusService.getSubmissionStatus(submissionId);

System.out.println("Status: " + response.getStatus());
System.out.println("Timestamp: " + response.getTimestamp());
System.out.println("Description: " + response.getDescription());
System.out.println("Ack Available: " + response.isAckAvailable());
```

### REST API Endpoint

```http
GET /api/mef/submissions/{submissionId}/status
```

**Response**:
```json
{
  "submissionId": "1234562025331test941",
  "status": "Accepted to MeF",
  "statusCode": null,
  "timestamp": "2025-12-08",
  "description": "This status record provides information...",
  "ackAvailable": true
}
```

---

## GetNewAcks Service

### Purpose

Retrieves new acknowledgments (not previously retrieved) from the IRS MeF system. Acknowledgments contain the final acceptance/rejection status and any validation errors.

### Service Endpoint

- **URL**: `https://la.alt.www4.irs.gov/a2a/mef/mime/GetNewAcks` (ATS)
- **Method**: SOAP 1.1 with MTOM attachments
- **Protocol**: HTTPS with mutual TLS authentication

### Request Parameters

#### SOAP Request Structure

```xml
<S:Envelope xmlns:S="http://schemas.xmlsoap.org/soap/envelope/">
  <S:Header>
    <!-- WS-Security header with SAML assertion -->
    <wsse:Security mustUnderstand="1">
      <wsu:Timestamp>
        <wsu:Created>2025-12-08T13:23:51Z</wsu:Created>
        <wsu:Expires>2025-12-08T13:28:51Z</wsu:Expires>
      </wsu:Timestamp>
      <wsse:UsernameToken>
        <wsse:Username>23868900</wsse:Username>
      </wsse:UsernameToken>
      <saml:Assertion>
        <!-- Full SAML assertion from login -->
      </saml:Assertion>
    </wsse:Security>

    <!-- MeF Header -->
    <MeFHeader xmlns="http://www.irs.gov/a2a/mef/MeFHeader.xsd">
      <MessageID>976612025342djhj07a2</MessageID>
      <Action>http://localhost:9080/a2a/mef/mime/GetNewAcks</Action>
      <MessageTs>2025-12-08T16:23:51+03:00</MessageTs>
      <ETIN>97661</ETIN>
      <SessionKeyCd>Y</SessionKeyCd>
      <TestCd>T</TestCd>
      <AppSysID>23868900</AppSysID>
      <WSDLVersionNum>10.9</WSDLVersionNum>
      <ClientSoftwareTxt>MeFA2AJavaToolkit2025v16.0</ClientSoftwareTxt>
    </MeFHeader>
  </S:Header>

  <S:Body>
    <ns2:GetNewAcksRequest xmlns:ns2="http://www.irs.gov/a2a/mef/MeFTransmitterService.xsd">
      <ns2:MaxResultCnt>100</ns2:MaxResultCnt>
    </ns2:GetNewAcksRequest>
  </S:Body>
</S:Envelope>
```

#### SDK Method Signature

```java
GetNewAcksResult invoke(ServiceContext context, Integer maxCount)
    throws ToolkitException, ServiceException

GetNewAcksResult invoke(
    ServiceContext context,
    Integer maxCount,
    ExtndAcknowledgementCategoryCdType category,
    GovernmentAgencyTypeCdType governmentAgency
) throws ToolkitException, ServiceException
```

#### Java Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `serviceContext` | `ServiceContext` | Yes | Session context with SAML assertion from login |
| `maxCount` | `Integer` | Yes | Maximum number of acknowledgments to retrieve (typically 100) |
| `category` | `ExtndAcknowledgementCategoryCdType` | No | Filter by ack category (null = all) |
| `governmentAgency` | `GovernmentAgencyTypeCdType` | No | Filter by agency (null = IRS) |

### Response Structure

#### SOAP Response Envelope

```xml
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
  <soapenv:Header>
    <!-- Echo of MeF header -->
    <ns1:MeFHeader xmlns:ns1="http://www.irs.gov/a2a/mef/MeFHeader.xsd">
      <ns1:MessageID>976612025342djhj07a2R</ns1:MessageID>
      <ns1:RelatesTo>976612025342djhj07a2</ns1:RelatesTo>
      <!-- ... other header fields ... -->
    </ns1:MeFHeader>
  </soapenv:Header>

  <soapenv:Body>
    <GetNewAcksResponse xmlns="http://www.irs.gov/a2a/mef/MeFTransmitterService.xsd">
      <MoreAvailableInd>false</MoreAvailableInd>
    </GetNewAcksResponse>
  </soapenv:Body>
</soapenv:Envelope>
```

#### MTOM Attachment (AcknowledgementList XML)

The acknowledgments are returned as a ZIP attachment containing an XML file with all acknowledgments:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<AcknowledgementList xmlns="http://www.irs.gov/efile" cnt="2">
  <Acknowledgement>
    <SubmissionId>1234562025331test941</SubmissionId>
    <EFIN>999999</EFIN>
    <SubmissionTyp>941</SubmissionTyp>
    <TaxYr>2026</TaxYr>
    <ElectronicPostmarkTs>2025-12-08T13:23:49.000Z</ElectronicPostmarkTs>
    <StatusDt>2025-12-08</StatusDt>
    <IRS SubmissionId>1234562025331test941</IRSSubmissionId>
    <EIN>003000004</EIN>
    <TaxPeriodEndDt>2026-03-31</TaxPeriodEndDt>
    <AcceptanceStatusTxt>Rejected</AcceptanceStatusTxt>
    <ReceiptId>CNNLP2U1LNH0GHJ84V1X993WJJ4KUJA295M6KSMC99W5Q</ReceiptId>

    <!-- If rejected, contains validation errors -->
    <ValidationErrorList>
      <ValidationError>
        <ErrorCategoryCd>Syntax</ErrorCategoryCd>
        <ErrorMessageTxt>Element 'TaxPeriodEndDt' value does not match pattern...</ErrorMessageTxt>
        <SeverityCd>Error</SeverityCd>
        <XPathContent>//TaxPeriodEndDt</XPathContent>
      </ValidationError>
    </ValidationErrorList>
  </Acknowledgement>

  <!-- Additional acknowledgments... -->
</AcknowledgementList>
```

#### SDK Result Object

```java
GetNewAcksResult result = client.invoke(serviceContext, maxCount);

// Check if more acknowledgments available
boolean moreAvailable = result.isMoreAvailableInd();

// Extract acknowledgment list
AcknowledgementList ackList = result.getAcknowledgementList();
List<Acknowledgement> acknowledgments = ackList.getAcknowledgements();

// Access acknowledgment details
for (Acknowledgement ack : acknowledgments) {
    String submissionId = ack.getSubmissionId();
    String acceptanceStatus = ack.getAcceptanceStatusTxt();
    String receiptId = ack.getReceiptId();
    String efin = ack.getEFIN();
    String tin = ack.getTIN();
    XMLGregorianCalendar statusDate = ack.getStatusDt();

    // Check for validation errors
    ValidationErrorListType errorList = ack.getValidationErrorList();
    if (errorList != null) {
        // Process errors
    }
}
```

### Response Fields

| Field | Type | Description |
|-------|------|-------------|
| `SubmissionId` | String | Original submission ID |
| `ReceiptId` | String | Unique acknowledgment receipt ID |
| `EFIN` | String | Electronic Filer Identification Number |
| `TIN` | String | Taxpayer Identification Number (EIN/SSN) |
| `SubmissionTyp` | String | Form type (e.g., "941") |
| `TaxYr` | String | Tax year |
| `AcceptanceStatusTxt` | String | Final status: "Accepted" or "Rejected" |
| `StatusDt` | XMLGregorianCalendar | Status date |
| `ElectronicPostmarkTs` | XMLGregorianCalendar | Time when submission was e-postmarked |
| `TaxPeriodEndDt` | XMLGregorianCalendar | Tax period end date |
| `ValidationErrorList` | ValidationErrorListType | List of errors if rejected |
| `MoreAvailableInd` | Boolean | True if more acknowledgments exist beyond maxCount |

### Acceptance Status Values

- **"Accepted"** - Return accepted by IRS, processing complete
- **"Rejected"** - Return rejected, must fix errors and resubmit

### Example Usage

```java
@Autowired
private AcknowledgementService acknowledgementService;

// Retrieve new acknowledgments
AckResponse.AckListResponse response = acknowledgementService.getNewAcknowledgments();

System.out.println("Total Count: " + response.getTotalCount());
System.out.println("Message: " + response.getMessage());

for (AckResponse ack : response.getAcknowledgments()) {
    System.out.println("Ack ID: " + ack.getAckId());
    System.out.println("Submission ID: " + ack.getSubmissionId());
    System.out.println("Status: " + ack.getAckType());
    System.out.println("Timestamp: " + ack.getTimestamp());
}
```

### REST API Endpoint

```http
GET /api/mef/acknowledgments/new
```

**Response**:
```json
{
  "acknowledgments": [
    {
      "ackId": "CNNLP2U1LNH0GHJ84V1X993WJJ4KUJA295M6KSMC99W5Q",
      "submissionId": "1234562025331test941",
      "ackType": "Rejected",
      "timestamp": "2025-12-08",
      "ackFilePath": null,
      "details": "Validation errors present in acknowledgment"
    }
  ],
  "totalCount": 2,
  "message": "Successfully retrieved all 2 new acknowledgments"
}
```

---

## Implementation Guide

### Prerequisites

1. **Active Session**: Must call `MefClientService.login()` first using certificate-only authentication
2. **ServiceContext**: Reuse the ServiceContext from login
3. **Dependencies**: MeF SDK v16 JARs in classpath
4. **Authentication**: X.509 certificate from IRS-approved CA (username/password not used)

### Implementation Pattern

Both services follow the same pattern:

```java
// 1. Get ServiceContext from login session
ServiceContext serviceContext = mefClientService.getCurrentServiceContext();

// 2. Create SDK client
GetSubmissionStatusClient statusClient = new GetSubmissionStatusClient();
// or
GetNewAcksClient ackClient = new GetNewAcksClient();

// 3. Invoke service
GetSubmissionStatusResult statusResult = statusClient.invoke(serviceContext, submissionId);
// or
GetNewAcksResult ackResult = ackClient.invoke(serviceContext, maxCount);

// 4. Extract and process results
StatusRecordList statusRecordList = statusResult.getStatusRecordList();
// or
AcknowledgementList ackList = ackResult.getAcknowledgementList();
```

### Error Handling

Both services handle three types of exceptions:

1. **MefException** - Custom application exceptions
2. **ServiceException** - IRS service errors
3. **ToolkitException** - SDK toolkit errors

```java
try {
    // Service call
} catch (MefException e) {
    throw e; // Re-throw custom exceptions
} catch (gov.irs.mef.exception.ServiceException e) {
    throw new MefException("IRS service error: " + e.getMessage(), ...);
} catch (gov.irs.mef.exception.ToolkitException e) {
    throw new MefException("MeF SDK error: " + e.getMessage(), ...);
}
```

---

## Testing

### Integration Test

The `Form941SubmissionTest` includes tests for both services:

```java
@Test
@Order(5)
public void test05_getSubmissionStatus() {
    StatusResponse statusResponse = statusService.getSubmissionStatus(submissionId);
    assertNotNull(statusResponse);
    assertNotNull(statusResponse.getStatus());
}

@Test
@Order(6)
public void test06_getNewAcknowledgments() {
    AckResponse.AckListResponse ackListResponse =
        acknowledgementService.getNewAcknowledgments();
    assertNotNull(ackListResponse);
    assertNotNull(ackListResponse.getAcknowledgments());
}
```

### Test Execution

**Note**: This implementation uses **certificate-only authentication**. Username/password are not required.

```bash
cd "mef-spring-boot-integration"

# Set Java 17
export JAVA_HOME=/opt/homebrew/opt/openjdk@17

# Set certificate-based authentication credentials
export MEF_KEYSTORE_PATH="/path/to/irs-mef-cert.p12"
export MEF_KEYSTORE_PASSWORD='your_keystore_password'
export MEF_KEYSTORE_TYPE=PKCS12
export MEF_KEY_ALIAS=your_cert_alias
export MEF_KEY_PASSWORD='your_key_password'

# Set IRS credentials
export MEF_ETIN=97661
export MEF_ASID=23868900

# Set SDK configuration path
export A2A_TOOLKIT_HOME="$(pwd)/src/main/resources/mef_config"

# Run test
mvn test -Dtest=Form941SubmissionTest
```

### Expected Results

- **GetSubmissionStatus**: Returns status "Accepted to MeF"
- **GetNewAcks**: Returns 2 acknowledgments (status depends on validation)

---

## Troubleshooting

### Common Issues

#### 1. "NOT_LOGGED_IN" Error

**Problem**: Service called before login or session expired

**Solution**:
```java
if (!mefClientService.isLoggedIn()) {
    LoginResponse loginResponse = mefClientService.login(loginRequest);
}
```

#### 2. "NO_STATUS_FOUND" Error

**Problem**: Submission ID not found or not yet processed

**Solution**: Wait a few minutes for IRS to process the submission before querying status

#### 3. Empty Acknowledgment List

**Problem**: No new acknowledgments available

**Solution**: IRS typically takes 2-5 minutes to generate acknowledgments after submission. Check `MoreAvailableInd` flag in response.

#### 4. ServiceException

**Problem**: IRS service returned an error

**Solution**: Check SDK logs in `a2a_sdk.log.*` files for detailed SOAP fault message

### Debug Logging

Enable debug logging to see full SOAP requests/responses:

```yaml
logging:
  level:
    gov.irs.mef: DEBUG
    com.irs.mef: DEBUG
```

SDK logs are written to:
- `a2a_sdk.log.*` - SOAP request/response details
- `logs/mef-spring-boot.log` - Application logs

---

## Summary

Both **GetSubmissionStatus** and **GetNewAcks** services are fully implemented and production-ready:

- ✅ Complete SOAP request/response handling
- ✅ SAML assertion authentication
- ✅ MTOM attachment processing
- ✅ Comprehensive error handling
- ✅ REST API endpoints
- ✅ Integration tests
- ✅ Full documentation

### Service Flow

1. **Login** → Get SAML assertion and ServiceContext
2. **Submit** → Send tax return, receive deposit ID
3. **GetSubmissionStatus** → Query processing status
4. **GetNewAcks** → Retrieve acceptance/rejection acknowledgment

### Key Files

- **StatusService**: `src/main/java/com/irs/mef/service/StatusService.java`
- **AcknowledgementService**: `src/main/java/com/irs/mef/service/AcknowledgementService.java`
- **Tests**: `src/test/java/com/irs/mef/scenarios/Form941SubmissionTest.java`
- **DTOs**: `src/main/java/com/irs/mef/dto/StatusResponse.java`, `AckResponse.java`

---

**Document maintained by**: MeF Spring Boot Integration Team
**Last successful test**: 2025-12-08 16:23:52+03:00
**IRS Environment**: ATS (Acceptance Testing System)
