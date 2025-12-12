# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot application integrating IRS Modernized e-File (MeF) Client SDK v16 for Application-to-Application (A2A) communication with IRS MeF servers. The main project is located in `mef-spring-boot-integration/`.

## Common Commands

### Building and Running

```bash
# Navigate to project directory first
cd "mef-spring-boot-integration"

# Build the project
mvn clean package

# Skip tests during build
mvn clean package -DskipTests

# Run the application (requires Java 17)
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
mvn spring-boot:run

# Run packaged JAR
java -jar target/mef-spring-boot-integration-1.0.0.jar
```

### Testing

```bash
cd "mef-spring-boot-integration"
mvn test
```

### Important JVM Requirements

When running manually with `java -jar`, these JVM arguments are required for Java 17+ compatibility with the MeF SDK:

```bash
java \
  -DA2A_TOOLKIT_HOME=./src/main/resources/mef_config \
  -Djava.endorsed.dirs=./lib \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-exports java.xml/com.sun.org.apache.xerces.internal.dom=ALL-UNNAMED \
  --add-exports java.xml.crypto/com.sun.org.apache.xml.internal.security=ALL-UNNAMED \
  --add-exports java.xml.crypto/org.jcp.xml.dsig.internal.dom=ALL-UNNAMED \
  -jar target/mef-spring-boot-integration-1.0.0.jar
```

Note: These are automatically configured in `pom.xml` when using `mvn spring-boot:run`.

## Architecture Overview

### Layered Architecture

The application follows a standard Spring Boot layered architecture:

1. **Controllers** (`controller/`) - REST API endpoints exposing MeF operations
   - `MefAuthController` - Login/logout endpoints
   - `SubmissionController` - Tax return submission endpoints
   - `AcknowledgementController` - Acknowledgment retrieval endpoints

2. **Services** (`service/`) - Business logic and MeF SDK integration
   - `MefClientService` - Core SDK operations, session management, login/logout
   - `SubmissionService` - Handles SendSubmissions calls
   - `StatusService` - Query submission status
   - `AcknowledgementService` - Retrieve acknowledgments

3. **DTOs** (`dto/`) - Data transfer objects for API requests/responses
   - Request/Response pairs for each operation (Login, Submit, Status, Ack)

4. **Configuration** (`config/`) - Spring configuration and property binding
   - `MefSdkConfig` - Binds `application.yml` properties to Java objects

5. **Exception Handling** (`exception/`)
   - `MefException` - Custom exception for MeF operations
   - `GlobalExceptionHandler` - Centralized exception handling

### MeF SDK Integration

The MeF Client SDK v16 JARs are located in `lib/` and referenced as system-scoped dependencies:
- `mef_client_sdk.jar` - Core MeF SDK
- `webservices-*.jar` - JAX-WS Metro libraries for SOAP services
- `xmlsec-4.0.2.jar` - XML security for WS-Security

SDK configuration files are in `src/main/resources/mef_config/`:
- `ats_endpoints.properties` / `prd_endpoints.properties` - Service URLs
- `*_client_security_config.xml` - WS-Security configurations
- `transport.properties`, `audit_log.properties`, `logging.properties`

### Session Management

The `MefClientService` maintains session state using reflection to call SDK classes:
- SAML assertion obtained from login is stored and reused for subsequent operations
- Session state is tracked with `isLoggedIn` flag
- Session ID is generated for tracking purposes

### SDK Implementation Status

**Implementation Progress** (Last Updated: 2025-12-08):

**✅ IMPLEMENTED AND WORKING**:
1. **Login Service** (`MefClientService.java:62-141`)
   - Certificate-based authentication with IRS MeF ATS
   - SAML assertion retrieval and session management
   - ServiceContext creation and preservation
   - Status: ✅ WORKING - Successfully authenticating with ETIN 97661

2. **SendSubmissions Service** (`SubmissionService.java:45-148`)
   - In-memory submission object creation (SubmissionXML, SubmissionManifest)
   - Manifest XML generation with required IRS fields
   - SubmissionBuilder pattern usage for archive creation
   - Submission ID validation (pattern: `[0-9]{13}[a-z0-9]{7}`)
   - Status: ✅ WORKING - Successfully submitting to IRS and receiving Deposit ID

3. **GetSubmissionStatus Service** (`StatusService.java:33-101`)
   - Query submission processing status by submission ID
   - Extracts StatusRecordList from MTOM attachment
   - Returns status text, acknowledgment date, and disclaimer
   - Status: ✅ WORKING - Successfully retrieving status from IRS

4. **GetNewAcks Service** (`AcknowledgementService.java:98-188`)
   - Retrieve new (not yet retrieved) acknowledgments
   - Processes AcknowledgementList with validation errors
   - Supports maxCount parameter (typically 100)
   - Indicates if more acknowledgments are available
   - Status: ✅ WORKING - Successfully retrieving acknowledgments from IRS

**❌ NOT YET IMPLEMENTED**:
- Logout service invocation (`MefClientService.java:157-177`)
- GetNewSubmissionsStatus service (bulk status query)
- GetAck service (retrieve specific acknowledgment by receipt ID)

**📚 DETAILED DOCUMENTATION**:
- **Form 941 Submission Guide**: `FORM_941_SUBMISSION_IMPLEMENTATION.md`
  - Complete error resolution timeline
  - Working code patterns for submission
  - In-memory vs file-based object handling
  - IRS service error investigation
  - Success: Submission accepted with Deposit ID
- **Submission ID Format**: `SUBMISSION_ID_FORMAT.md`
  - Regex pattern requirements discovered via bytecode analysis
  - Valid/invalid examples and testing methods
- **Status and Acknowledgments Guide**: `STATUS_AND_ACK_SERVICES_DOCUMENTATION.md`
  - Complete SOAP request/response specifications
  - Full parameter documentation
  - Example usage and REST API endpoints
  - Troubleshooting guide

## Configuration

### Environment Variables

Required for IRS certificate-based authentication:
```bash
MEF_ETIN=your_etin
MEF_ASID=your_asid
MEF_KEYSTORE_PATH=/path/to/keystore.p12
MEF_KEYSTORE_PASSWORD=your_keystore_password
MEF_KEYSTORE_TYPE=PKCS12
MEF_KEY_ALIAS=your_certificate_alias
MEF_KEY_PASSWORD=your_key_password
```

**Note**: This implementation uses **certificate-only authentication**. Username/password authentication is not used. The certificate must be IRS-approved and properly configured in the keystore.

### Application Configuration

Primary configuration: `src/main/resources/application.yml`
- Server runs on port 8080 with context path `/api`
- Environment selection: `mef.sdk.environment` (ATS or PRD)
- Timeouts: Connection and read timeouts (default 600 seconds)
- Logging: DEBUG level for `com.irs.mef` and `gov.irs.mef`

### Certificate Requirements

IRS MeF requires X.509 client certificates for strong authentication:
- Supported formats: PKCS12 (.p12, .pfx) or JKS
- Certificate must be IRS-approved CA-signed
- Configure via `MEF_KEYSTORE_PATH` and related environment variables

## REST API Endpoints

Base URL: `http://localhost:8080/api`

### Authentication
- `POST /mef/auth/login` - Login with ETIN, username, password
- `POST /mef/auth/logout` - Logout from MeF
- `GET /mef/auth/status` - Check session status

### Submissions
- `POST /mef/submissions/submit` - Submit tax return
- `GET /mef/submissions/{submissionId}/status` - Get submission status
- `GET /mef/submissions/status/new` - Get new submissions status

### Acknowledgments
- `GET /mef/acknowledgments/{ackId}` - Get specific acknowledgment
- `GET /mef/acknowledgments/new` - Get new acknowledgments
- `GET /mef/acknowledgments/submission/{submissionId}` - Get acks by submission

## Development Notes

### SDK Class Loading Pattern

The codebase uses reflection to load MeF SDK classes dynamically (see `MefClientService.java:72-105`). This pattern should be followed when implementing other SDK operations:

```java
Class<?> serviceClass = Class.forName("gov.irs.mef.services.SomeService");
Object serviceInstance = serviceClass.getDeclaredConstructor().newInstance();
Object result = serviceClass.getMethod("methodName", paramTypes).invoke(serviceInstance, params);
```

### Form 941 Submission Implementation

**Status**: Partially working - Login successful, submission encountering IRS service error

**Complete Implementation Guide**: See `FORM_941_SUBMISSION_IMPLEMENTATION.md` for:
- Detailed error resolution timeline (3 major errors solved)
- Working code patterns for login and submission
- In-memory vs file-based object handling (critical SDK requirement)
- Submission ID format requirements (`[0-9]{13}[a-z0-9]{7}`)
- Manifest XML generation and required fields
- Current IRS service error investigation

**Key Learnings**:
1. **Submission ID Format**: Must be exactly 20 chars (13 digits + 7 lowercase alphanumeric)
2. **Manifest Required**: Cannot be null, must include EFIN, TIN, tax period dates
3. **In-Memory Objects**: SDK serialization requires in-memory constructors for SubmissionXML and SubmissionManifest
4. **ServiceContext Preservation**: Must reuse same ServiceContext from login for all operations

### Implementation Status by Service

**✅ Implemented**:
- `MefClientService.java:62-141` - Login service (WORKING)
- `SubmissionService.java:45-148` - SendSubmissions service (PARTIAL - IRS error)
- `SubmissionService.java:233-269` - Manifest XML generation
- `MefClientService.java` - ServiceContext management

**❌ TODO**:
- `MefClientService.java:157-177` - Logout service invocation
- `StatusService.java` - GetSubmissionStatus and GetNewSubmissionsStatus calls
- `AcknowledgementService.java` - GetAck and GetNewAcks calls
- IRS service error resolution (see FORM_941_SUBMISSION_IMPLEMENTATION.md)

### Logs

Application logs: `logs/mef-spring-boot.log`
SDK audit logs: `logs/mef_audit_log.txt`
SDK debug logs: `a2a_sdk.log.*` (in project root)

### IRS Environments

- **ATS** (Acceptance Testing System): Test environment at `https://la.alt.www4.irs.gov`
- **PRD** (Production): Production environment at `https://www.irs.gov`

Configure via `mef.sdk.environment` in `application.yml`.

## SDK Reference Documentation (RECOMMENDED)

**CRITICAL**: When implementing SDK integrations, ALWAYS consult the SDK reference documentation first to understand the actual API structure, method signatures, and available classes. Do not guess or assume SDK APIs exist.

### Quick Start - SDK Documentation

The MeF SDK has been **extracted and documented** for efficient development. Use these resources:

**Primary Documentation** (located in `mef-spring-boot-integration/sdk-reference/docs/`):

1. **SDK_API_REFERENCE.md** - Comprehensive API reference with all service classes, method signatures, and examples
2. **SDK_WSDL_GUIDE.md** - WSDL service definitions and XML schemas
3. **SDK_QUICK_REF.md** - Quick reference with code patterns and examples

**Extracted SDK** (located in `mef-spring-boot-integration/sdk-reference/extracted/`):
- All class files from `mef_client_sdk.jar` (310 service classes)
- WSDL files in `META-INF/wsdl/` - Authoritative service definitions
- XSD schemas for request/response structures

### Efficient SDK Exploration Workflow

**Step 1: Check the documentation first**
```bash
cd "mef-spring-boot-integration"

# View API reference for all service classes
cat sdk-reference/docs/SDK_API_REFERENCE.md

# View WSDL guide for service definitions
cat sdk-reference/docs/SDK_WSDL_GUIDE.md

# View quick reference for code patterns
cat sdk-reference/docs/SDK_QUICK_REF.md
```

**Step 2: Search extracted files (10-100x faster than javap)**
```bash
cd "mef-spring-boot-integration/sdk-reference/extracted"

# Find all service client classes
find gov/irs/mef/services -name "*Client.class"

# Find classes by keyword
find . -name "*Submission*.class"

# Search for specific functionality
grep -r "Acknowledgment" gov/irs/mef/services/
```

**Step 3: Inspect WSDL for authoritative service definitions**
```bash
# View transmitter services (Submit, Status, Ack)
cat sdk-reference/extracted/META-INF/wsdl/MeFTransmitterServicesMTOM.wsdl

# View MSI services (Login, Logout)
cat sdk-reference/extracted/META-INF/wsdl/MeFMSIServices.wsdl

# View XML schemas
cat sdk-reference/extracted/META-INF/wsdl/MeFHeader.xsd
```

**Step 4: Use javap for detailed method signatures (when needed)**
```bash
# Inspect specific class
javap -classpath lib/mef_client_sdk.jar -public gov.irs.mef.services.transmitter.SendSubmissionsClient
```

### Regenerating SDK Documentation

If SDK documentation becomes outdated or you need to regenerate it:

```bash
cd "mef-spring-boot-integration/sdk-reference/scripts"
./generate-sdk-docs.sh
```

This script will:
- Analyze all SDK classes using javap
- Generate comprehensive API reference
- Create WSDL guide with all service definitions
- Build quick reference with code patterns

### Key SDK Packages

The extracted SDK contains these key packages:

- **`gov.irs.mef.services.msi.*`** - MSI services (Login, Logout)
- **`gov.irs.mef.services.transmitter.*`** - Transmitter services (Submit, Status, Ack)
- **`gov.irs.mef.services.data.*`** - Data objects (ETIN, etc.)
- **`gov.irs.a2a.mef.mefheader.*`** - MeF headers (TestCdType, etc.)
- **`gov.irs.mef.exception.*`** - Exception classes
- **`gov.irs.mef.services.util.*`** - Utility classes

### Workflow for Implementing New SDK Operations

1. **Consult SDK_QUICK_REF.md** - Check for existing code patterns and examples
2. **Review SDK_API_REFERENCE.md** - Find the service class and method signatures
3. **Check WSDL files** - Understand request/response structures (most authoritative)
4. **Search extracted files** - Use grep/find for fast discovery
5. **Review existing code** - See `MefClientService.java:62-141` for reflection pattern
6. **Implement using reflection** - Follow the established pattern
7. **Test incrementally** - Verify each SDK call works before moving to the next

### Example: Implementing SendSubmissions

```bash
# Step 1: Check quick reference
cat sdk-reference/docs/SDK_QUICK_REF.md | grep -A 10 "Submit Example"

# Step 2: View API reference
cat sdk-reference/docs/SDK_API_REFERENCE.md | grep -A 20 "SendSubmissionsClient"

# Step 3: Inspect WSDL for request/response structure
cat sdk-reference/extracted/META-INF/wsdl/MeFTransmitterServicesMTOM.wsdl | grep -A 30 "SendSubmissions"

# Step 4: Get exact method signature (if needed)
javap -classpath lib/mef_client_sdk.jar -public gov.irs.mef.services.transmitter.SendSubmissionsClient
```

---

## Legacy JAR Inspection Methods (For Reference)

The following methods are **legacy approaches** that required repeated tool invocations. They are preserved here for reference, but the **extracted SDK and documentation** above are now the recommended approach.

<details>
<summary>Click to expand legacy methods</summary>

### Legacy Method 1: List JAR Contents

```bash
cd "mef-spring-boot-integration/lib"
jar tf mef_client_sdk.jar | grep "services.*\.class$"
```

**Note:** This is slower than searching extracted files with `find` or `grep`.

### Legacy Method 2: Inspect with javap

```bash
javap -classpath lib/mef_client_sdk.jar -public gov.irs.mef.services.msi.LoginClient
```

**Note:** This still works but requires knowing the exact class name. The extracted SDK documentation provides comprehensive API reference.

### Legacy Method 3: Manual JAR Extraction

```bash
mkdir sdk_extracted && cd sdk_extracted
jar xf ../lib/mef_client_sdk.jar
```

**Note:** This is now done automatically in `sdk-reference/extracted/`.

</details>

## References

See `README.md` for:
- Detailed certificate setup instructions
- IRS MeF enrollment requirements
- API endpoint documentation with examples
- Troubleshooting common issues
- Links to IRS documentation
