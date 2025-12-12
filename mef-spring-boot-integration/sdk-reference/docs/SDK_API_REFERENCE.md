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

## Core Service Classes

### ServiceContext

Primary context object for all SDK operations.

**Package:** `gov.irs.mef.services`

Compiled from "ServiceContext.java"
public class gov.irs.mef.services.ServiceContext {
  public gov.irs.mef.services.ServiceContext(gov.irs.mef.services.data.ETIN, java.lang.String, gov.irs.a2a.mef.mefheader.TestCdType);
  public java.lang.String getAppSysID();
  public void setAppSysID(java.lang.String);
  public gov.irs.mef.services.data.ETIN getEtin();
  public void setEtin(gov.irs.mef.services.data.ETIN);
  public gov.irs.mef.services.SessionInfo getSessionInfo();
  public void setSessionInfo(gov.irs.mef.services.SessionInfo);
  public gov.irs.a2a.mef.mefheader.TestCdType getTestCdType();
  public void setTestCdType(gov.irs.a2a.mef.mefheader.TestCdType);
  public java.lang.String getClientSoftwareTxt();
  public void setClientSoftwareTxt(java.lang.String);
}

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

Compiled from "ServiceClient.java"
public abstract class gov.irs.mef.services.ServiceClient<R extends gov.irs.mef.services.Result, T extends gov.irs.mef.services.Request> {
  public java.lang.String getMessageID();
  public void setReadTimeout(int);
  public void setConnectTimeout(int);
}

---

## MSI Services

### LoginClient

Authenticates with IRS MeF and obtains SAML assertion.

**Package:** `gov.irs.mef.services.msi`

Compiled from "LoginClient.java"
public class gov.irs.mef.services.msi.LoginClient extends gov.irs.mef.services.ServiceClient<gov.irs.mef.services.msi.LoginResult, gov.irs.mef.services.msi.LoginRequest> {
  public gov.irs.mef.services.msi.LoginClient();
  public gov.irs.mef.services.msi.LoginResult invoke(gov.irs.mef.services.ServiceContext, java.io.File, java.lang.String, java.lang.String, java.lang.String, java.lang.String) throws gov.irs.mef.exception.ToolkitException, gov.irs.mef.exception.ServiceException;
  public gov.irs.mef.services.msi.LoginResult invoke(gov.irs.mef.services.ServiceContext, java.lang.String) throws gov.irs.mef.exception.ToolkitException, gov.irs.mef.exception.ServiceException;
  public gov.irs.mef.services.msi.LoginResult invoke(gov.irs.mef.services.ServiceContext, java.io.File, java.lang.String, java.lang.String) throws gov.irs.mef.exception.ToolkitException, gov.irs.mef.exception.ServiceException;
}

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

Compiled from "LogoutClient.java"
public class gov.irs.mef.services.msi.LogoutClient extends gov.irs.mef.services.ServiceClient<gov.irs.mef.services.msi.LogoutResult, gov.irs.mef.services.Request> {
  public gov.irs.mef.services.msi.LogoutClient();
  public gov.irs.mef.services.msi.LogoutResult invoke(gov.irs.mef.services.ServiceContext) throws gov.irs.mef.exception.ToolkitException, gov.irs.mef.exception.ServiceException;
}

**Invoke Method:**
```java
LogoutResult invoke(ServiceContext context)
    throws ToolkitException, ServiceException
```

---

### LoginResult / LogoutResult

Result objects for MSI operations.

**Package:** `gov.irs.mef.services.msi`

Compiled from "LoginResult.java"
public class gov.irs.mef.services.msi.LoginResult extends gov.irs.mef.services.BaseResult<gov.irs.mef.services.msi.LoginResult> {
  public java.lang.String getStatusTxt();
}

---

## Transmitter Services

### SendSubmissionsClient

Submits tax returns to IRS MeF.

**Package:** `gov.irs.mef.services.transmitter`

Compiled from "SendSubmissionsClient.java"
public class gov.irs.mef.services.transmitter.SendSubmissionsClient extends gov.irs.mef.services.AttachmentReceivingClient<gov.irs.mef.services.transmitter.SendSubmissionsResult, gov.irs.mef.services.BasicAttachmentRequest<gov.irs.mef.inputcomposition.SubmissionContainer>> {
  public gov.irs.mef.services.transmitter.SendSubmissionsClient(java.io.File);
  public gov.irs.mef.services.transmitter.SendSubmissionsClient(java.lang.String);
  public gov.irs.mef.services.transmitter.SendSubmissionsClient();
  public gov.irs.mef.services.transmitter.SendSubmissionsResult invoke(gov.irs.mef.services.ServiceContext, gov.irs.mef.inputcomposition.SubmissionContainer) throws gov.irs.mef.exception.ToolkitException, gov.irs.mef.exception.ServiceException;
}

---

### GetSubmissionStatusClient

Retrieves status of submitted returns.

**Package:** `gov.irs.mef.services.transmitter`

Compiled from "GetSubmissionStatusClient.java"
public class gov.irs.mef.services.transmitter.GetSubmissionStatusClient extends gov.irs.mef.services.AttachmentReceivingClient<gov.irs.mef.services.transmitter.GetSubmissionStatusResult, gov.irs.mef.services.BasicSubmissionIdRequest> {
  public gov.irs.mef.services.transmitter.GetSubmissionStatusClient(java.io.File);
  public gov.irs.mef.services.transmitter.GetSubmissionStatusClient(java.lang.String);
  public gov.irs.mef.services.transmitter.GetSubmissionStatusClient();
  public gov.irs.mef.services.transmitter.GetSubmissionStatusResult invoke(gov.irs.mef.services.ServiceContext, java.lang.String) throws gov.irs.mef.exception.ToolkitException, gov.irs.mef.exception.ServiceException;
}

---

### GetNewSubmissionsStatusClient

Retrieves status for new submissions.

**Package:** `gov.irs.mef.services.transmitter`

Compiled from "GetNewSubmissionsStatusClient.java"
public class gov.irs.mef.services.transmitter.GetNewSubmissionsStatusClient extends gov.irs.mef.services.AttachmentReceivingClient<gov.irs.mef.services.transmitter.GetNewSubmissionsStatusResult, gov.irs.mef.services.BasicBoundedResultRequest> {
  public gov.irs.mef.services.transmitter.GetNewSubmissionsStatusClient(java.io.File);
  public gov.irs.mef.services.transmitter.GetNewSubmissionsStatusClient(java.lang.String);
  public gov.irs.mef.services.transmitter.GetNewSubmissionsStatusClient();
  public gov.irs.mef.services.transmitter.GetNewSubmissionsStatusResult invoke(gov.irs.mef.services.ServiceContext, java.lang.Integer) throws gov.irs.mef.exception.ToolkitException, gov.irs.mef.exception.ServiceException;
}

---

### GetAckClient

Retrieves acknowledgments by message ID.

**Package:** `gov.irs.mef.services.transmitter`

Compiled from "GetAckClient.java"
public class gov.irs.mef.services.transmitter.GetAckClient extends gov.irs.mef.services.AttachmentReceivingClient<gov.irs.mef.services.transmitter.GetAckResult, gov.irs.mef.services.BasicSubmissionIdRequest> {
  public gov.irs.mef.services.transmitter.GetAckClient(java.io.File);
  public gov.irs.mef.services.transmitter.GetAckClient(java.lang.String);
  public gov.irs.mef.services.transmitter.GetAckClient();
  public gov.irs.mef.services.transmitter.GetAckResult invoke(gov.irs.mef.services.ServiceContext, java.lang.String) throws gov.irs.mef.exception.ToolkitException, gov.irs.mef.exception.ServiceException;
}

---

### GetNewAcksClient

Retrieves new acknowledgments.

**Package:** `gov.irs.mef.services.transmitter`

Compiled from "GetNewAcksClient.java"
public class gov.irs.mef.services.transmitter.GetNewAcksClient extends gov.irs.mef.services.AttachmentReceivingClient<gov.irs.mef.services.transmitter.GetNewAcksResult, gov.irs.mef.services.transmitter.GetNewAcksRequest> {
  public gov.irs.mef.services.transmitter.GetNewAcksClient(java.io.File);
  public gov.irs.mef.services.transmitter.GetNewAcksClient(java.lang.String);
  public gov.irs.mef.services.transmitter.GetNewAcksClient();
  public gov.irs.mef.services.transmitter.GetNewAcksResult invoke(gov.irs.mef.services.ServiceContext, java.lang.Integer) throws gov.irs.mef.exception.ToolkitException, gov.irs.mef.exception.ServiceException;
  public gov.irs.mef.services.transmitter.GetNewAcksResult invoke(gov.irs.mef.services.ServiceContext, java.lang.Integer, gov.irs.a2a.mef.meftransmitterservice.ExtndAcknowledgementCategoryCdType, gov.irs.a2a.mef.meftransmitterservice.GovernmentAgencyTypeCdType) throws gov.irs.mef.exception.ToolkitException, gov.irs.mef.exception.ServiceException;
}

---

## Data Classes

### ETIN (Electronic Transmitter Identification Number)

**Package:** `gov.irs.mef.services.data`

Compiled from "ETIN.java"
public class gov.irs.mef.services.data.ETIN {
  public static final java.lang.String ETIN_RESTRICTION;
  public gov.irs.mef.services.data.ETIN(java.lang.String);
  public java.lang.String getValue();
  public java.lang.String toString();
  public static boolean isValidETIN(java.lang.String);
  public boolean equals(java.lang.Object);
  public int hashCode();
}

**Constructor:**
```java
ETIN(String etin)
```

---

### TestCdType

Enum for test mode selection.

**Package:** `gov.irs.a2a.mef.mefheader`

Compiled from "TestCdType.java"
public final class gov.irs.a2a.mef.mefheader.TestCdType extends java.lang.Enum<gov.irs.a2a.mef.mefheader.TestCdType> {
  public static final gov.irs.a2a.mef.mefheader.TestCdType T;
  public static final gov.irs.a2a.mef.mefheader.TestCdType P;
  public static gov.irs.a2a.mef.mefheader.TestCdType[] values();
  public static gov.irs.a2a.mef.mefheader.TestCdType valueOf(java.lang.String);
  public java.lang.String value();
  public static gov.irs.a2a.mef.mefheader.TestCdType fromValue(java.lang.String);
}

**Values:**
- `PRODUCTION` - Production mode
- `TEST` - Test mode

---

### SessionInfo

Tracks session state and SAML assertion.

**Package:** `gov.irs.mef.services`

Compiled from "SessionInfo.java"
public class gov.irs.mef.services.SessionInfo {
  public gov.irs.mef.services.SessionInfo();
  public void setSAMLToken(org.w3c.dom.Element);
  public org.w3c.dom.Element getSAMLToken();
}

---

## Exception Classes

### ToolkitException

Base exception for SDK errors.

**Package:** `gov.irs.mef.exception`

Compiled from "ToolkitException.java"
public class gov.irs.mef.exception.ToolkitException extends java.lang.Exception {
  public gov.irs.mef.exception.ToolkitException(java.lang.String, java.lang.Throwable);
  public gov.irs.mef.exception.ToolkitException(java.lang.String);
  public gov.irs.mef.exception.ToolkitException(java.lang.String, java.lang.Throwable, java.util.logging.Level);
  public gov.irs.mef.exception.ToolkitException(java.lang.String, java.util.logging.Level);
  public java.util.logging.Level getSeverity();
  public void setSeverity(java.util.logging.Level);
}

---

### ServiceException

Exception for service-level errors.

**Package:** `gov.irs.mef.exception`

Compiled from "ServiceException.java"
public class gov.irs.mef.exception.ServiceException extends java.lang.Exception {
  public gov.irs.mef.exception.ServiceException(java.lang.String, gov.irs.a2a.mef.etectransmitterservice.ErrorExceptionDetail, java.util.logging.Level);
  public gov.irs.mef.exception.ServiceException(java.lang.String, gov.irs.a2a.mef.etectransmitterservicemtom.ErrorExceptionDetail, java.util.logging.Level);
  public gov.irs.mef.exception.ServiceException(java.lang.String, gov.irs.a2a.mef.mefstateservicemtom.ErrorExceptionDetail, java.util.logging.Level);
  public gov.irs.mef.exception.ServiceException(java.lang.String, gov.irs.a2a.mef.mefstateservice.ErrorExceptionDetail, java.util.logging.Level);
  public gov.irs.mef.exception.ServiceException(java.lang.String, gov.irs.a2a.mef.meftransmitterservice.ErrorExceptionDetail, java.util.logging.Level);
  public gov.irs.mef.exception.ServiceException(java.lang.String, gov.irs.a2a.mef.meftransmitterservicemtom.ErrorExceptionDetail, java.util.logging.Level);
  public gov.irs.mef.exception.ServiceException(java.lang.String, gov.irs.a2a.mef.mefmsiservices.ErrorExceptionDetail, java.util.logging.Level);
  public java.util.logging.Level getSeverity();
  public void setSeverity(java.util.logging.Level);
  public java.lang.String getErrorClassification();
  public java.lang.String getErrorCode();
  public java.lang.String getErrorMessage();
  public java.lang.String getType();
  public java.lang.String getHostName();
}

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
