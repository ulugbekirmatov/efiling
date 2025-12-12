#!/bin/bash

# MeF SDK Documentation Generator
# This script analyzes the extracted MeF SDK and generates comprehensive documentation

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
EXTRACTED_DIR="$SDK_ROOT/extracted"
DOCS_DIR="$SDK_ROOT/docs"
JAR_PATH="$SDK_ROOT/../lib/mef_client_sdk.jar"

echo "=== MeF SDK Documentation Generator ==="
echo "SDK Root: $SDK_ROOT"
echo "Extracted: $EXTRACTED_DIR"
echo "Docs Output: $DOCS_DIR"
echo ""

# Verify extracted directory exists
if [ ! -d "$EXTRACTED_DIR" ]; then
    echo "ERROR: Extracted directory not found at $EXTRACTED_DIR"
    echo "Please extract the JAR first: cd sdk-reference/extracted && jar xf ../../lib/mef_client_sdk.jar"
    exit 1
fi

# Generate SDK API Reference
echo "Generating SDK_API_REFERENCE.md..."
cat > "$DOCS_DIR/SDK_API_REFERENCE.md" << 'EOF_HEADER'
# MeF SDK API Reference

This document provides comprehensive API reference for the IRS MeF Client SDK v16.

**Generated from:** `mef_client_sdk.jar`
**Extraction Date:** $(date)

## Table of Contents

1. [Core Service Classes](#core-service-classes)
2. [MSI Services (Login/Logout)](#msi-services)
3. [Transmitter Services (Submit/Status/Ack)](#transmitter-services)
4. [Data Classes](#data-classes)
5. [Exception Classes](#exception-classes)
6. [Utility Classes](#utility-classes)

---

EOF_HEADER

# Generate Core Service Classes section
echo "Analyzing core service classes..."
cat >> "$DOCS_DIR/SDK_API_REFERENCE.md" << 'EOF_CORE'
## Core Service Classes

### ServiceContext

Primary context object for all SDK operations.

**Package:** `gov.irs.mef.services`

EOF_CORE

# Use javap to get ServiceContext details
javap -classpath "$JAR_PATH" -public gov.irs.mef.services.ServiceContext >> "$DOCS_DIR/SDK_API_REFERENCE.md" 2>/dev/null || echo "Could not analyze ServiceContext"

cat >> "$DOCS_DIR/SDK_API_REFERENCE.md" << 'EOF_CORE2'

**Constructor:**
```java
ServiceContext(ETIN etin, String appSysID, TestCdType testMode)
```

**Key Methods:**
- `getEtin()` - Get the ETIN
- `getAppSysID()` - Get Application System ID
- `getSessionInfo()` - Get current session information
- `setAppSysID(String)` - Set Application System ID

---

### ServiceClient

Base class for all service clients.

**Package:** `gov.irs.mef.services`

EOF_CORE2

javap -classpath "$JAR_PATH" -public gov.irs.mef.services.ServiceClient >> "$DOCS_DIR/SDK_API_REFERENCE.md" 2>/dev/null || echo "Could not analyze ServiceClient"

# Generate MSI Services section
cat >> "$DOCS_DIR/SDK_API_REFERENCE.md" << 'EOF_MSI'

---

## MSI Services

### LoginClient

Authenticates with IRS MeF and obtains SAML assertion.

**Package:** `gov.irs.mef.services.msi`

EOF_MSI

javap -classpath "$JAR_PATH" -public gov.irs.mef.services.msi.LoginClient >> "$DOCS_DIR/SDK_API_REFERENCE.md" 2>/dev/null || echo "Could not analyze LoginClient"

cat >> "$DOCS_DIR/SDK_API_REFERENCE.md" << 'EOF_MSI2'

**Invoke Method:**
```java
LoginResult invoke(
    ServiceContext context,
    File keystoreFile,
    String keystorePassword,
    String keyAlias,
    String username,
    String password
) throws ToolkitException, ServiceException
```

**Returns:** `LoginResult` containing SAML assertion

---

### LogoutClient

Terminates MeF session.

**Package:** `gov.irs.mef.services.msi`

EOF_MSI2

javap -classpath "$JAR_PATH" -public gov.irs.mef.services.msi.LogoutClient >> "$DOCS_DIR/SDK_API_REFERENCE.md" 2>/dev/null || echo "Could not analyze LogoutClient"

cat >> "$DOCS_DIR/SDK_API_REFERENCE.md" << 'EOF_MSI3'

**Invoke Method:**
```java
LogoutResult invoke(ServiceContext context)
    throws ToolkitException, ServiceException
```

---

### LoginResult / LogoutResult

Result objects for MSI operations.

**Package:** `gov.irs.mef.services.msi`

EOF_MSI3

javap -classpath "$JAR_PATH" -public gov.irs.mef.services.msi.LoginResult >> "$DOCS_DIR/SDK_API_REFERENCE.md" 2>/dev/null || echo "Could not analyze LoginResult"

# Generate Transmitter Services section
cat >> "$DOCS_DIR/SDK_API_REFERENCE.md" << 'EOF_TRANS'

---

## Transmitter Services

### SendSubmissionsClient

Submits tax returns to IRS MeF.

**Package:** `gov.irs.mef.services.transmitter`

EOF_TRANS

javap -classpath "$JAR_PATH" -public gov.irs.mef.services.transmitter.SendSubmissionsClient >> "$DOCS_DIR/SDK_API_REFERENCE.md" 2>/dev/null || echo "Could not analyze SendSubmissionsClient"

cat >> "$DOCS_DIR/SDK_API_REFERENCE.md" << 'EOF_TRANS2'

---

### GetSubmissionStatusClient

Retrieves status of submitted returns.

**Package:** `gov.irs.mef.services.transmitter`

EOF_TRANS2

javap -classpath "$JAR_PATH" -public gov.irs.mef.services.transmitter.GetSubmissionStatusClient >> "$DOCS_DIR/SDK_API_REFERENCE.md" 2>/dev/null || echo "Could not analyze GetSubmissionStatusClient"

cat >> "$DOCS_DIR/SDK_API_REFERENCE.md" << 'EOF_TRANS3'

---

### GetNewSubmissionsStatusClient

Retrieves status for new submissions.

**Package:** `gov.irs.mef.services.transmitter`

EOF_TRANS3

javap -classpath "$JAR_PATH" -public gov.irs.mef.services.transmitter.GetNewSubmissionsStatusClient >> "$DOCS_DIR/SDK_API_REFERENCE.md" 2>/dev/null || echo "Could not analyze GetNewSubmissionsStatusClient"

cat >> "$DOCS_DIR/SDK_API_REFERENCE.md" << 'EOF_TRANS4'

---

### GetAckClient

Retrieves acknowledgments by message ID.

**Package:** `gov.irs.mef.services.transmitter`

EOF_TRANS4

javap -classpath "$JAR_PATH" -public gov.irs.mef.services.transmitter.GetAckClient >> "$DOCS_DIR/SDK_API_REFERENCE.md" 2>/dev/null || echo "Could not analyze GetAckClient"

cat >> "$DOCS_DIR/SDK_API_REFERENCE.md" << 'EOF_TRANS5'

---

### GetNewAcksClient

Retrieves new acknowledgments.

**Package:** `gov.irs.mef.services.transmitter`

EOF_TRANS5

javap -classpath "$JAR_PATH" -public gov.irs.mef.services.transmitter.GetNewAcksClient >> "$DOCS_DIR/SDK_API_REFERENCE.md" 2>/dev/null || echo "Could not analyze GetNewAcksClient"

# Generate Data Classes section
cat >> "$DOCS_DIR/SDK_API_REFERENCE.md" << 'EOF_DATA'

---

## Data Classes

### ETIN (Electronic Transmitter Identification Number)

**Package:** `gov.irs.mef.services.data`

EOF_DATA

javap -classpath "$JAR_PATH" -public gov.irs.mef.services.data.ETIN >> "$DOCS_DIR/SDK_API_REFERENCE.md" 2>/dev/null || echo "Could not analyze ETIN"

cat >> "$DOCS_DIR/SDK_API_REFERENCE.md" << 'EOF_DATA2'

**Constructor:**
```java
ETIN(String etin)
```

---

### TestCdType

Enum for test mode selection.

**Package:** `gov.irs.a2a.mef.mefheader`

EOF_DATA2

javap -classpath "$JAR_PATH" -public -constants gov.irs.a2a.mef.mefheader.TestCdType >> "$DOCS_DIR/SDK_API_REFERENCE.md" 2>/dev/null || echo "Could not analyze TestCdType"

cat >> "$DOCS_DIR/SDK_API_REFERENCE.md" << 'EOF_DATA3'

**Values:**
- `PRODUCTION` - Production mode
- `TEST` - Test mode

---

### SessionInfo

Tracks session state and SAML assertion.

**Package:** `gov.irs.mef.services`

EOF_DATA3

javap -classpath "$JAR_PATH" -public gov.irs.mef.services.SessionInfo >> "$DOCS_DIR/SDK_API_REFERENCE.md" 2>/dev/null || echo "Could not analyze SessionInfo"

# Generate Exception Classes section
cat >> "$DOCS_DIR/SDK_API_REFERENCE.md" << 'EOF_EXC'

---

## Exception Classes

### ToolkitException

Base exception for SDK errors.

**Package:** `gov.irs.mef.exception`

EOF_EXC

javap -classpath "$JAR_PATH" -public gov.irs.mef.exception.ToolkitException >> "$DOCS_DIR/SDK_API_REFERENCE.md" 2>/dev/null || echo "Could not analyze ToolkitException"

cat >> "$DOCS_DIR/SDK_API_REFERENCE.md" << 'EOF_EXC2'

---

### ServiceException

Exception for service-level errors.

**Package:** `gov.irs.mef.exception`

EOF_EXC2

javap -classpath "$JAR_PATH" -public gov.irs.mef.exception.ServiceException >> "$DOCS_DIR/SDK_API_REFERENCE.md" 2>/dev/null || echo "Could not analyze ServiceException"

# Generate Utility Classes section
cat >> "$DOCS_DIR/SDK_API_REFERENCE.md" << 'EOF_UTIL'

---

## Utility Classes

### Class Discovery

To find additional classes, use grep on the extracted directory:

```bash
# Find all service clients
find sdk-reference/extracted/gov/irs/mef/services -name "*Client.class"

# Find all result classes
find sdk-reference/extracted/gov/irs/mef/services -name "*Result.class"

# Search for specific functionality
grep -r "Acknowledgment" sdk-reference/extracted/gov/irs/mef/services/
```

### Inspection Commands

```bash
# Inspect any class using javap
javap -classpath lib/mef_client_sdk.jar -public gov.irs.mef.services.ClassName

# List all classes in a package
jar tf lib/mef_client_sdk.jar | grep "gov/irs/mef/services/transmitter"
```

---

**End of API Reference**
EOF_UTIL

echo "SDK_API_REFERENCE.md generated successfully!"

# Generate WSDL Guide
echo ""
echo "Generating SDK_WSDL_GUIDE.md..."
cat > "$DOCS_DIR/SDK_WSDL_GUIDE.md" << 'EOF_WSDL_HEADER'
# MeF SDK WSDL & Schema Guide

This document catalogs all WSDL service definitions and XSD schemas in the MeF SDK.

**Location:** `sdk-reference/extracted/META-INF/wsdl/`

## Available WSDL Files

EOF_WSDL_HEADER

# List all WSDL files
find "$EXTRACTED_DIR/META-INF/wsdl" -name "*.wsdl" -exec basename {} \; | sort | while read wsdl_file; do
    echo "- **$wsdl_file**" >> "$DOCS_DIR/SDK_WSDL_GUIDE.md"
done

cat >> "$DOCS_DIR/SDK_WSDL_GUIDE.md" << 'EOF_WSDL_BODY'

## Available XSD Schema Files

EOF_WSDL_BODY

# List all XSD files
find "$EXTRACTED_DIR/META-INF/wsdl" -name "*.xsd" -exec basename {} \; | sort | while read xsd_file; do
    echo "- **$xsd_file**" >> "$DOCS_DIR/SDK_WSDL_GUIDE.md"
done

cat >> "$DOCS_DIR/SDK_WSDL_GUIDE.md" << 'EOF_WSDL_SERVICES'

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
EOF_WSDL_SERVICES

echo "SDK_WSDL_GUIDE.md generated successfully!"

# Generate Quick Reference
echo ""
echo "Generating SDK_QUICK_REF.md..."
cat > "$DOCS_DIR/SDK_QUICK_REF.md" << 'EOF_QUICK'
# MeF SDK Quick Reference

Essential patterns and examples for implementing MeF SDK operations.

## Quick Navigation

- [Login Example](#login-example)
- [Logout Example](#logout-example)
- [Submit Example](#submit-example)
- [Status Check Example](#status-check-example)
- [Acknowledgment Example](#acknowledgment-example)
- [Class Mapping Table](#class-mapping-table)
- [Common Patterns](#common-patterns)

---

## Login Example

```java
// Using reflection pattern (see MefClientService.java:62-141)
Class<?> loginClientClass = Class.forName("gov.irs.mef.services.msi.LoginClient");
Class<?> serviceContextClass = Class.forName("gov.irs.mef.services.ServiceContext");
Class<?> etinClass = Class.forName("gov.irs.mef.services.data.ETIN");
Class<?> testCdTypeClass = Class.forName("gov.irs.a2a.mef.mefheader.TestCdType");

// Create ETIN
Object etin = etinClass.getConstructor(String.class).newInstance(etinValue);

// Get TestCdType enum value
Object testMode = testCdTypeClass.getMethod("fromValue", String.class)
    .invoke(null, isTestMode ? "T" : "P");

// Create ServiceContext
Object serviceContext = serviceContextClass
    .getConstructor(etinClass, String.class, testCdTypeClass)
    .newInstance(etin, appSysId, testMode);

// Invoke login
Object loginClient = loginClientClass.getDeclaredConstructor().newInstance();
Object loginResult = loginClientClass.getMethod(
    "invoke",
    serviceContextClass,
    File.class,
    String.class,
    String.class,
    String.class,
    String.class
).invoke(
    loginClient,
    serviceContext,
    keystoreFile,
    keystorePassword,
    keyAlias,
    username,
    password
);

// Extract SAML assertion
Class<?> loginResultClass = Class.forName("gov.irs.mef.services.msi.LoginResult");
String assertion = (String) loginResultClass.getMethod("getAssertion")
    .invoke(loginResult);
```

---

## Logout Example

```java
// Reuse ServiceContext from login
Class<?> logoutClientClass = Class.forName("gov.irs.mef.services.msi.LogoutClient");

Object logoutClient = logoutClientClass.getDeclaredConstructor().newInstance();
Object logoutResult = logoutClientClass.getMethod("invoke", serviceContextClass)
    .invoke(logoutClient, serviceContext);
```

---

## Submit Example

```java
// TODO: Inspect SendSubmissionsClient for exact method signature
Class<?> submitClientClass = Class.forName(
    "gov.irs.mef.services.transmitter.SendSubmissionsClient"
);

// Expected pattern (verify with javap):
// SendSubmissionsResult invoke(
//     ServiceContext context,
//     File submissionFile,
//     ...
// )

// Use javap to discover exact signature:
// javap -classpath lib/mef_client_sdk.jar -public \
//   gov.irs.mef.services.transmitter.SendSubmissionsClient
```

---

## Status Check Example

```java
// TODO: Inspect GetSubmissionStatusClient for exact method signature
Class<?> statusClientClass = Class.forName(
    "gov.irs.mef.services.transmitter.GetSubmissionStatusClient"
);

// Expected pattern (verify with javap):
// GetSubmissionStatusResult invoke(
//     ServiceContext context,
//     String submissionId
// )
```

---

## Acknowledgment Example

```java
// TODO: Inspect GetAckClient for exact method signature
Class<?> ackClientClass = Class.forName(
    "gov.irs.mef.services.transmitter.GetAckClient"
);

// Expected pattern (verify with javap):
// GetAckResult invoke(
//     ServiceContext context,
//     String messageId
// )
```

---

## Class Mapping Table

| Operation | WSDL Service | SDK Client Class | Result Class | Request Type |
|-----------|--------------|------------------|--------------|--------------|
| **Login** | MSI:Login | `gov.irs.mef.services.msi.LoginClient` | `LoginResult` | File, String... |
| **Logout** | MSI:Logout | `gov.irs.mef.services.msi.LogoutClient` | `LogoutResult` | ServiceContext |
| **Submit** | SendSubmissions | `gov.irs.mef.services.transmitter.SendSubmissionsClient` | `SendSubmissionsResult` | TBD (inspect JAR) |
| **Status** | GetSubmissionStatus | `gov.irs.mef.services.transmitter.GetSubmissionStatusClient` | `GetSubmissionStatusResult` | TBD |
| **New Status** | GetNewSubmissionsStatus | `gov.irs.mef.services.transmitter.GetNewSubmissionsStatusClient` | `GetNewSubmissionsStatusResult` | TBD |
| **Get Ack** | GetAck | `gov.irs.mef.services.transmitter.GetAckClient` | `GetAckResult` | TBD |
| **New Acks** | GetNewAcks | `gov.irs.mef.services.transmitter.GetNewAcksClient` | `GetNewAcksResult` | TBD |

---

## Common Patterns

### Pattern 1: Load SDK Class

```java
Class<?> sdkClass = Class.forName("gov.irs.mef.services.package.ClassName");
```

### Pattern 2: Create Instance

```java
Object instance = sdkClass.getDeclaredConstructor().newInstance();
```

### Pattern 3: Invoke Method

```java
Object result = sdkClass.getMethod("methodName", paramTypes...)
    .invoke(instance, params...);
```

### Pattern 4: Extract Result Data

```java
Class<?> resultClass = Class.forName("gov.irs.mef.services.package.ResultClass");
Object data = resultClass.getMethod("getData").invoke(result);
```

### Pattern 5: Handle Exceptions

```java
try {
    // SDK call
} catch (InvocationTargetException e) {
    Throwable cause = e.getCause();
    if (cause.getClass().getName().equals("gov.irs.mef.exception.ServiceException")) {
        // Handle ServiceException
    } else if (cause.getClass().getName().equals("gov.irs.mef.exception.ToolkitException")) {
        // Handle ToolkitException
    }
}
```

---

## Discovery Commands

### Find All Client Classes

```bash
find sdk-reference/extracted -name "*Client.class" | \
  sed 's|sdk-reference/extracted/||' | \
  sed 's|/|.|g' | \
  sed 's|.class$||'
```

### Find All Result Classes

```bash
find sdk-reference/extracted -name "*Result.class" | \
  sed 's|sdk-reference/extracted/||' | \
  sed 's|/|.|g' | \
  sed 's|.class$||'
```

### Search for Specific Functionality

```bash
# Find classes related to acknowledgments
grep -r "Ack" sdk-reference/extracted/gov/irs/mef/services/ | grep "\.class"

# Find classes related to status
grep -r "Status" sdk-reference/extracted/gov/irs/mef/services/ | grep "\.class"
```

### Inspect Method Signatures

```bash
# General pattern
javap -classpath lib/mef_client_sdk.jar -public gov.irs.mef.services.package.ClassName

# Example for SendSubmissionsClient
javap -classpath lib/mef_client_sdk.jar -public \
  gov.irs.mef.services.transmitter.SendSubmissionsClient
```

---

**End of Quick Reference**
EOF_QUICK

echo "SDK_QUICK_REF.md generated successfully!"

echo ""
echo "=== Documentation Generation Complete ==="
echo ""
echo "Generated files:"
echo "  - $DOCS_DIR/SDK_API_REFERENCE.md"
echo "  - $DOCS_DIR/SDK_WSDL_GUIDE.md"
echo "  - $DOCS_DIR/SDK_QUICK_REF.md"
echo ""
echo "To view documentation:"
echo "  cat $DOCS_DIR/SDK_API_REFERENCE.md"
echo "  cat $DOCS_DIR/SDK_WSDL_GUIDE.md"
echo "  cat $DOCS_DIR/SDK_QUICK_REF.md"
echo ""
