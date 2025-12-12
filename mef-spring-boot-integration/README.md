# MeF Spring Boot Integration

Spring Boot application integrating **IRS Modernized e-File (MeF) Client SDK v16** for Application-to-Application (A2A) communication with IRS MeF servers.

## Overview

This application provides REST API endpoints to interact with IRS MeF A2A services including:

- **Authentication**: Login/Logout with IRS MeF servers
- **Submissions**: Submit tax returns to IRS
- **Status Queries**: Check submission processing status
- **Acknowledgments**: Retrieve acceptance/rejection acknowledgments

## Table of Contents

- [Prerequisites](#prerequisites)
- [Project Structure](#project-structure)
- [Configuration](#configuration)
- [Certificate Setup](#certificate-setup)
- [Building the Application](#building-the-application)
- [Running the Application](#running-the-application)
- [API Endpoints](#api-endpoints)
- [Troubleshooting](#troubleshooting)
- [References](#references)

## Prerequisites

### Required Software

- **Java 17** (or later)
- **Maven 3.6+**
- **IRS-approved CA-signed X.509 client certificate** (for authentication)

### IRS MeF Enrollment

Before using this application, you must:

1. **Enroll with IRS MeF** - Complete the automated enrollment process to receive:
   - Electronic Transmitter Identification Number (ETIN)
   - Application System ID (ASID)
   - Username and password for A2A access

2. **Obtain Client Certificate** - Get an IRS-approved CA-signed X.509 certificate for strong authentication

3. **Register Certificate** - Register your client certificate with IRS MeF system

See: [IRS Strong Authentication User Guide](https://www.irs.gov/e-file-providers) for enrollment details.

## Project Structure

```
mef-spring-boot-integration/
├── pom.xml                                 # Maven build configuration
├── README.md                               # This file
├── lib/                                    # MeF SDK JAR files
│   ├── mef_client_sdk.jar
│   ├── webservices-api-4.0.4.jar
│   ├── webservices-rt-4.0.4.jar
│   ├── webservices-extra-4.0.4.jar
│   ├── webservices-tools-4.0.4.jar
│   └── xmlsec-4.0.2.jar
├── src/main/
│   ├── java/com/irs/mef/
│   │   ├── MefSpringBootApplication.java  # Main application class
│   │   ├── config/                        # Configuration classes
│   │   │   └── MefSdkConfig.java
│   │   ├── controller/                    # REST API controllers
│   │   │   ├── MefAuthController.java     # Login/logout endpoints
│   │   │   ├── SubmissionController.java  # Submission endpoints
│   │   │   └── AcknowledgementController.java
│   │   ├── service/                       # Business logic services
│   │   │   ├── MefClientService.java
│   │   │   ├── SubmissionService.java
│   │   │   ├── StatusService.java
│   │   │   └── AcknowledgementService.java
│   │   ├── dto/                           # Data transfer objects
│   │   │   ├── LoginRequest.java
│   │   │   ├── LoginResponse.java
│   │   │   ├── SubmitRequest.java
│   │   │   ├── SubmitResponse.java
│   │   │   ├── StatusResponse.java
│   │   │   └── AckResponse.java
│   │   └── exception/                     # Exception handling
│   │       ├── MefException.java
│   │       └── GlobalExceptionHandler.java
│   └── resources/
│       ├── application.yml                # Spring Boot configuration
│       └── mef_config/                    # MeF SDK configuration files
│           ├── ats_endpoints.properties   # Test environment endpoints
│           ├── prd_endpoints.properties   # Production endpoints
│           ├── login_client_security_config.xml
│           ├── basic_a2a_client_security_config.xml
│           ├── logout_client_security_config.xml
│           ├── transport.properties
│           ├── audit_log.properties
│           └── logging.properties
└── logs/                                  # Application logs (created at runtime)
```

## Configuration

### 1. Environment Variables

Set the following environment variables or configure them in `application.yml`:

```bash
# Required: IRS MeF Credentials
export MEF_ETIN=your_etin_here
export MEF_USERNAME=your_username_here
export MEF_PASSWORD=your_password_here
export MEF_ASID=your_asid_here

# Required: Client Certificate
export MEF_KEYSTORE_PATH=/path/to/your/keystore.p12
export MEF_KEYSTORE_PASSWORD=your_keystore_password
export MEF_KEY_ALIAS=your_key_alias
export MEF_KEY_PASSWORD=your_key_password

# Optional: Trust Store (if using custom trust store)
export MEF_TRUSTSTORE_PATH=/path/to/truststore.jks
export MEF_TRUSTSTORE_PASSWORD=changeit
```

### 2. Application Configuration

Edit `src/main/resources/application.yml` to customize:

- **Server port**: Default is 8080
- **Environment**: `ATS` (test) or `PRD` (production)
- **Timeouts**: Connection and read timeout values
- **Logging levels**: Adjust log verbosity

### 3. SDK Configuration Files

SDK configuration files in `src/main/resources/mef_config/` include:

- **ats_endpoints.properties**: Test environment service URLs
- **prd_endpoints.properties**: Production environment service URLs
- **transport.properties**: Connection timeouts and HTTP settings
- **audit_log.properties**: Audit logging configuration
- **logging.properties**: SDK logging configuration
- **Security XML files**: WS-Security configuration for SOAP messages

## Certificate Setup

### Overview

IRS MeF requires **strong authentication** using X.509 client certificates for all A2A communications.

### Obtaining Certificates

1. **Generate Private Key**: Use browser or OpenSSL to generate private key
2. **Create Certificate Signing Request (CSR)**
3. **Submit CSR** to IRS-approved Certificate Authority (CA)
4. **Receive CA-Signed Certificate**
5. **Import Certificate** into keystore

### Certificate Formats

The SDK supports the following keystore formats:

- **PKCS12** (`.p12`, `.pfx`) - Recommended
- **JKS** (Java KeyStore)
- **Windows Personal Current User Store** (Windows only, Java 6+)

### Creating a PKCS12 Keystore

If you have a certificate (`.cer`) and private key (`.key`):

```bash
# Combine certificate and key into PKCS12 keystore
openssl pkcs12 -export \
  -in certificate.cer \
  -inkey private_key.key \
  -out keystore.p12 \
  -name "mef_client_cert"
```

### Installing into Java KeyStore

If you need to convert PKCS12 to JKS:

```bash
keytool -importkeystore \
  -srckeystore keystore.p12 \
  -srcstoretype PKCS12 \
  -destkeystore keystore.jks \
  -deststoretype JKS
```

### Trust Store Configuration

IRS signer certificates for SSL/TLS verification:

```bash
# Import IRS CA certificate into trust store
keytool -import \
  -alias irs_ca_cert \
  -file irs_ca.cer \
  -keystore truststore.jks \
  -storepass changeit
```

### Configuration in application.yml

```yaml
mef:
  sdk:
    certificate:
      keystore-path: /path/to/keystore.p12
      keystore-password: your_password
      keystore-type: PKCS12
      key-alias: mef_client_cert
      key-password: your_key_password
    ssl:
      trust-store-path: /path/to/truststore.jks
      trust-store-password: changeit
      trust-store-type: JKS
```

## Building the Application

### Compile and Package

```bash
# Clean and build
mvn clean package

# Skip tests
mvn clean package -DskipTests
```

### Build Output

The build creates an executable JAR:
```
target/mef-spring-boot-integration-1.0.0.jar
```

## Running the Application

### Using Maven

```bash
# Run with Maven Spring Boot plugin
mvn spring-boot:run
```

### Using Java JAR

```bash
# Run the packaged JAR
java -jar target/mef-spring-boot-integration-1.0.0.jar
```

### With Custom Configuration

```bash
# Override configuration via command line
java -jar target/mef-spring-boot-integration-1.0.0.jar \
  --mef.sdk.environment=ATS \
  --server.port=8081
```

### With JVM Arguments (Required for Java 17+)

The application automatically sets required JVM arguments, but if running manually:

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

## API Endpoints

Base URL: `http://localhost:8080/api`

### Authentication Endpoints

#### Login
```http
POST /api/mef/auth/login
Content-Type: application/json

{
  "etin": "12345678",
  "username": "testuser",
  "password": "password",
  "productionMode": false
}
```

**Response:**
```json
{
  "success": true,
  "samlAssertion": "...",
  "sessionId": "SESSION_123456",
  "timestamp": "2025-11-10T16:00:00",
  "message": "Login successful"
}
```

#### Logout
```http
POST /api/mef/auth/logout
```

**Response:**
```json
{
  "success": true,
  "message": "Logged out successfully"
}
```

#### Session Status
```http
GET /api/mef/auth/status
```

**Response:**
```json
{
  "loggedIn": true,
  "sessionId": "SESSION_123456"
}
```

### Submission Endpoints

#### Submit Tax Return
```http
POST /api/mef/submissions/submit
Content-Type: application/json

{
  "submissionId": "SUB123456",
  "submissionFilePath": "/path/to/submission.zip",
  "productionMode": false,
  "manifest": "...",
  "metadata": "..."
}
```

**Response:**
```json
{
  "submissionId": "SUB123456",
  "messageId": "MSG_1699999999",
  "status": "Accepted",
  "timestamp": "2025-11-10T16:05:00",
  "message": "Submission sent successfully",
  "accepted": true
}
```

#### Get Submission Status
```http
GET /api/mef/submissions/{submissionId}/status
```

**Response:**
```json
{
  "submissionId": "SUB123456",
  "status": "Accepted",
  "statusCode": "200",
  "timestamp": "2025-11-10T16:10:00",
  "description": "Submission processed successfully",
  "ackAvailable": true
}
```

#### Get New Submissions Status
```http
GET /api/mef/submissions/status/new
```

**Response:**
```json
[
  {
    "submissionId": "SUB123456",
    "status": "Accepted",
    "statusCode": "200",
    "timestamp": "2025-11-10T16:10:00",
    "ackAvailable": true
  }
]
```

### Acknowledgment Endpoints

#### Get Specific Acknowledgment
```http
GET /api/mef/acknowledgments/{ackId}
```

**Response:**
```json
{
  "ackId": "ACK123456",
  "submissionId": "SUB123456",
  "ackType": "Accept",
  "timestamp": "2025-11-10T16:15:00",
  "ackFilePath": "acks/ACK123456.xml",
  "details": "Acknowledgment retrieved successfully"
}
```

#### Get New Acknowledgments
```http
GET /api/mef/acknowledgments/new
```

**Response:**
```json
{
  "acknowledgments": [
    {
      "ackId": "ACK123456",
      "submissionId": "SUB123456",
      "ackType": "Accept",
      "timestamp": "2025-11-10T16:15:00",
      "ackFilePath": "acks/ACK123456.xml"
    }
  ],
  "totalCount": 1,
  "message": "Successfully retrieved new acknowledgments"
}
```

#### Get Acknowledgments by Submission
```http
GET /api/mef/acknowledgments/submission/{submissionId}
```

## Troubleshooting

### Common Issues

#### 1. Certificate Not Found

**Error:** `KeyStore file not found` or `Certificate authentication failed`

**Solution:**
- Verify `MEF_KEYSTORE_PATH` points to correct file
- Ensure keystore password is correct
- Check certificate is valid and not expired

#### 2. Connection Timeout

**Error:** `Connection timeout` or `Read timeout`

**Solution:**
- Check network connectivity to IRS servers
- Verify firewall allows HTTPS outbound connections
- Increase timeout values in `application.yml`

#### 3. SDK Initialization Failed

**Error:** `A2A_TOOLKIT_HOME not set` or `Configuration files not found`

**Solution:**
- Ensure `A2A_TOOLKIT_HOME` system property is set
- Verify SDK config files exist in `src/main/resources/mef_config/`
- Check file permissions

#### 4. Java Module Access Errors

**Error:** `InaccessibleObjectException` or `IllegalAccessError`

**Solution:**
- Ensure JVM arguments are set (automatically handled by Spring Boot plugin)
- For manual runs, add `--add-opens` and `--add-exports` flags as shown above

### Enable Debug Logging

In `application.yml`:

```yaml
logging:
  level:
    com.irs.mef: DEBUG
    gov.irs.mef: DEBUG
```

### Check Logs

Application logs:
```
logs/mef-spring-boot.log
```

SDK audit logs:
```
logs/mef_audit_log.txt
```

## Implementation Notes

### SDK Integration Status

This project includes:

- Complete project structure with Maven configuration
- All SDK JAR files and configuration files integrated
- Service layer with placeholder implementations
- REST API controllers for all operations
- Comprehensive error handling
- Full documentation

### TODO: Complete SDK Implementation

The service classes contain placeholder implementations with `TODO` comments indicating where actual MeF SDK API calls should be integrated. To complete the implementation:

1. Review SDK User Guide (`MeF-Client-SDK_UserGuide.doc`)
2. Import SDK classes in service files
3. Replace placeholder code with actual SDK API calls
4. Configure security handlers for WS-Security
5. Test with IRS ATS (test) environment
6. Validate with actual certificates and credentials

Example areas needing SDK integration:
- `MefClientService.java:54` - Login service invocation
- `MefClientService.java:118` - Logout service invocation
- `SubmissionService.java:66` - SendSubmissions service call
- `StatusService.java:48` - GetSubmissionStatus service call
- `AcknowledgementService.java:49` - GetAck service call

## References

### IRS MeF Documentation

- [MeF A2A Overview](https://www.irs.gov/e-file-providers/modernized-e-file-mef-for-software-developers)
- MeF Client SDK Installation Guide (Version 16.0)
- MeF Client SDK User Guide (Version 16.0)
- IRS Strong Authentication User Guide
- Automated Enrollment External User Guide

### MeF SDK Files

- SDK Location: `Version_16/A2A_Toolkit_Version16.0/MeF_Client_SDK/`
- Documentation: `Version_16/A2A_Toolkit_Version16.0/MeF_Documentation/`
- Client Apps: `Version_16/A2A_Toolkit_Version16.0/MeF_Client_Apps/`

### Support

For IRS MeF support:
- MeF Help Desk: Contact via IRS e-Services portal
- Software Developer Support: See IRS Publication 5830

## License

This project integrates IRS MeF Client SDK. Please review IRS terms and conditions for MeF A2A services.

---

**Version:** 1.0.0
**Last Updated:** November 2025
**SDK Version:** MeF Client SDK v16.0
