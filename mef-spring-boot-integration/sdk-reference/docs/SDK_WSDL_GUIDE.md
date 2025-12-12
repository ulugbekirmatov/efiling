# MeF SDK WSDL & Schema Guide

This document catalogs all WSDL service definitions and XSD schemas in the MeF SDK.

**Location:** `sdk-reference/extracted/META-INF/wsdl/`

## Available WSDL Files

- **ETECTransmitterServicesMIME.wsdl**
- **ETECTransmitterServicesMTOM.wsdl**
- **MeFMSIServices.wsdl**
- **MeFStateServicesMIME.wsdl**
- **MeFStateServicesMTOM.wsdl**
- **MeFTransmitterServicesMIME.wsdl**
- **MeFTransmitterServicesMTOM.wsdl**

## Available XSD Schema Files

- **efileAttachments.xsd**
- **efileTypes.xsd**
- **ETECTransmitterService.xsd**
- **ETECTransmitterServiceMTOM.xsd**
- **MeFHeader.xsd**
- **MeFMSIServices.xsd**
- **MeFStateService.xsd**
- **MeFStateServiceMTOM.xsd**
- **MeFTransmitterService.xsd**
- **MeFTransmitterServiceMTOM.xsd**
- **xmime.xsd**

---

## Service Definitions

### MeFMSIServices.wsdl

**MSI (MeF Service Interface)** - Login/Logout operations

**Location:** `sdk-reference/extracted/META-INF/wsdl/MeFMSIServices.wsdl`

**Operations:**
- **Login** - Authenticate and obtain SAML assertion
- **Logout** - Terminate session

**To inspect:**
```bash
cat sdk-reference/extracted/META-INF/wsdl/MeFMSIServices.wsdl
```

---

### MeFTransmitterServicesMTOM.wsdl

**Transmitter Services** - Tax return submission and status operations

**Location:** `sdk-reference/extracted/META-INF/wsdl/MeFTransmitterServicesMTOM.wsdl`

**Operations:**
- **SendSubmissions** - Submit tax returns
- **GetSubmissionStatus** - Get status by submission ID
- **GetNewSubmissionsStatus** - Get status for new submissions
- **GetAck** - Get acknowledgment by message ID
- **GetNewAcks** - Get new acknowledgments

**To inspect:**
```bash
cat sdk-reference/extracted/META-INF/wsdl/MeFTransmitterServicesMTOM.wsdl
```

---

### MeFStateServicesMTOM.wsdl

**State Services** - State tax return operations

**Location:** `sdk-reference/extracted/META-INF/wsdl/MeFStateServicesMTOM.wsdl`

**To inspect:**
```bash
cat sdk-reference/extracted/META-INF/wsdl/MeFStateServicesMTOM.wsdl
```

---

### ETECTransmitterServicesMTOM.wsdl

**ETEC Services** - Electronic Tax Education Council operations

**Location:** `sdk-reference/extracted/META-INF/wsdl/ETECTransmitterServicesMTOM.wsdl`

**To inspect:**
```bash
cat sdk-reference/extracted/META-INF/wsdl/ETECTransmitterServicesMTOM.wsdl
```

---

## Key XSD Schemas

### MeFHeader.xsd

Defines MeF header structure for all submissions.

**Location:** `sdk-reference/extracted/META-INF/wsdl/MeFHeader.xsd`

**Key Elements:**
- TestCd - Test mode indicator (T/P)
- SubmissionId - Unique submission identifier
- ETIN - Electronic Transmitter ID

---

### efileTypes.xsd

Core type definitions for e-file operations.

**Location:** `sdk-reference/extracted/META-INF/wsdl/efileTypes.xsd`

---

### efileAttachments.xsd

Defines attachment structure for submissions.

**Location:** `sdk-reference/extracted/META-INF/wsdl/efileAttachments.xsd`

---

## WSDL Inspection Workflow

1. **Identify the service** you want to implement (e.g., SendSubmissions)

2. **Find the WSDL file:**
   ```bash
   grep -r "SendSubmissions" sdk-reference/extracted/META-INF/wsdl/
   ```

3. **View the WSDL:**
   ```bash
   cat sdk-reference/extracted/META-INF/wsdl/MeFTransmitterServicesMTOM.wsdl
   ```

4. **Examine request/response types:**
   ```bash
   cat sdk-reference/extracted/META-INF/wsdl/MeFTransmitterService.xsd
   ```

5. **Map to SDK client class:**
   - WSDL operation `SendSubmissions` → SDK class `SendSubmissionsClient`
   - Request type → Constructor/method parameters
   - Response type → Result object structure

---

**End of WSDL Guide**
