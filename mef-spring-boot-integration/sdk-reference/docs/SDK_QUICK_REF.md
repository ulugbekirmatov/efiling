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
