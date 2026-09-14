# MeF Integration Code Map

Spring Boot 3.2.0 / Java 17 application wrapping the IRS Modernized e-File (MeF) Client SDK v16 for A2A communication. Project root: `mef-spring-boot-integration/` inside the `MeF` repo.

All paths below are relative to `/Users/ulugbekirmatov/Documents/MeF/` unless prefixed. Line numbers verified against the files as they exist on disk (2026-08-28).

---

## 1. Directory & Package Tree

### Repo root

| Path | Purpose |
|---|---|
| `mef-spring-boot-integration/` | The Spring Boot application (everything below) |
| `Version_16/A2A_Toolkit_Version16.0/` | Vendor drop of the IRS A2A Toolkit v16. `MeF_Client_SDK/Java/dist/mef_client_sdk.zip` (17 MB) holds the six SDK jars; they are **not** unpacked and `*.jar` is gitignored |
| `Version_15/` | Previous toolkit version, unused by the build |
| `test-scenarios/941-scenario-1-orchid-q1-2026/` | `Return941-Scenario1.xml` + supporting files consumed by the Form 941 tests |
| `94x-2026/`, `Pyramos Software Certificates/` | Schema/cert reference material, not on the build path |
| `create-pkcs12.sh`, `verify-pkcs12.sh` | Keystore helper scripts |

### Application tree

```
mef-spring-boot-integration/
├── pom.xml
├── src/main/java/com/irs/mef/
│   ├── MefSpringBootApplication.java        entry point; sets SDK system properties
│   ├── config/
│   │   ├── MefSdkConfig.java                @ConfigurationProperties("mef.sdk")
│   │   ├── RetryConfig.java                 RetryTemplate bean
│   │   └── DotenvEnvironmentPostProcessor.java  loads .env before property binding
│   ├── controller/
│   │   ├── MefAuthController.java           /mef/auth/**
│   │   ├── SubmissionController.java        /mef/submissions/**
│   │   └── AcknowledgementController.java   /mef/acknowledgments/**
│   ├── service/
│   │   ├── MefClientService.java            login, session state, cert diagnostics
│   │   ├── SubmissionService.java           SendSubmissions
│   │   ├── StatusService.java               GetSubmissionStatus
│   │   └── AcknowledgementService.java      GetAck / GetNewAcks
│   ├── dto/
│   │   ├── LoginRequest.java   LoginResponse.java
│   │   ├── SubmitRequest.java  SubmitResponse.java
│   │   ├── StatusResponse.java AckResponse.java (+ nested AckListResponse)
│   │   └── CertificateTestResponse.java     (+ 4 nested result types)
│   └── exception/
│       ├── MefException.java                RuntimeException + errorCode/detail
│       └── GlobalExceptionHandler.java      @RestControllerAdvice
├── src/main/resources/
│   ├── application.yml                      all mef.sdk.* keys, logging, actuator
│   ├── META-INF/spring.factories            registers DotenvEnvironmentPostProcessor
│   └── mef_config/config/                   A2A_TOOLKIT_HOME contents (see §5)
├── src/test/java/com/irs/mef/
│   ├── config/CertificateLoadingTest.java   pure-JCE keystore tests
│   ├── service/MefLoginIntegrationTest.java live IRS login, system-property gated
│   ├── scenarios/Form941SubmissionTest.java @SpringBootTest end-to-end vs. ATS
│   ├── scenarios/Form941XmlGenerationTest.java  XSD + field validation, offline
│   └── xml/{ReturnXmlGenerator,XmlValidator}.java  test-only helpers
├── src/test/resources/
│   ├── schemas/941/*.xsd                    IRS941, Return941, ReturnData941, ReturnHeader94x, efileTypes
│   └── test-scenarios/941-scenario-1/scenario-config.yml  expected values for Scenario 1
└── sdk-reference/
    ├── docs/{SDK_API_REFERENCE,SDK_WSDL_GUIDE,SDK_QUICK_REF}.md   generated SDK docs
    ├── extracted/META-INF/                  MANIFEST, jax-ws-catalog.xml, wsdl/ (18 WSDL/XSD files)
    └── scripts/generate-sdk-docs.sh         regenerates docs/ from the jar
```

**`lib/` does not exist.** Older docs describe system-scoped jars in `lib/`; the current `pom.xml` resolves the SDK from the local Maven repository instead. See §5 and §7.

**`sdk-reference/extracted/` holds only 21 files** — the WSDL/XSD set under `META-INF/`. The "310 extracted class files" described in older docs are gone (`*.class` is gitignored).

---

## 2. Classes

### `com.irs.mef.MefSpringBootApplication`
`src/main/java/com/irs/mef/MefSpringBootApplication.java`

`@SpringBootApplication @EnableScheduling`. Sets two system properties before the Spring context starts.

| Method | Line | Notes |
|---|---|---|
| `main(String[])` | 27 | calls `configureMefSdkSystemProperties()` then `SpringApplication.run` |
| `configureMefSdkSystemProperties()` | 38 | private static |

Line 43-44 sets `A2A_TOOLKIT_HOME` to `${user.dir}/src/main/resources/mef_config`. Line 51-52 sets `java.endorsed.dirs` to `${user.dir}/lib` — **a directory that does not exist**.

### `com.irs.mef.config.MefSdkConfig`
`src/main/java/com/irs/mef/config/MefSdkConfig.java` — `@Configuration @ConfigurationProperties(prefix = "mef.sdk") @Data`

| Member | Line |
|---|---|
| `init()` (`@PostConstruct`) | 71 |
| `isCertificateConfigured()` | 89 |
| `isAuthenticationConfigured()` | 101 — requires ETIN **and** ASID |
| `getBaseUrl()` | 112 — PRD vs ATS switch |
| nested `Transport` | 120 |
| nested `Audit` | 128 |
| nested `Ssl` | 134 |
| nested `Certificate` | 141 |
| nested `Authentication` | 151 — etin, efin, username, password, asid |
| nested `Endpoints` | 160 |
| nested `EndpointConfig` (`getFullUrl` at 177) | 166 |
| nested `Security` | 183 |

`init()` only prints; the commented-out `ApplicationContext.setToolkitHome(...)` at line 76 means `mef.sdk.toolkit-home` is never actually pushed into the SDK — the SDK reads the system property set in `MefSpringBootApplication` instead.

### `com.irs.mef.config.RetryConfig`
`src/main/java/com/irs/mef/config/RetryConfig.java`

| Method | Line |
|---|---|
| `mefRetryTemplate()` `@Bean` | 40 |

Exponential backoff: 5 s initial, ×3.0, capped at 60 s (lines 45-47). `SimpleRetryPolicy(3, {Exception.class: true}, true)` at lines 56-60 — **retries every `Exception`, including `MefException`**, despite the "Don't retry MefException" comments in the services (`StatusService.java:111`, `AcknowledgementService.java:136/242/363`). A `NOT_LOGGED_IN` or `NO_STATUS_FOUND` failure is therefore retried three times with backoff before surfacing.

### `com.irs.mef.config.DotenvEnvironmentPostProcessor`
`src/main/java/com/irs/mef/config/DotenvEnvironmentPostProcessor.java`

| Method | Line |
|---|---|
| `postProcessEnvironment(ConfigurableEnvironment, SpringApplication)` | 32 |

Registered via `src/main/resources/META-INF/spring.factories`. Reads `.env` from `user.dir`, skips keys already present in the OS environment (line 50), and appends the map with `addLast` so real env vars win. Masks values whose key contains `PASSWORD` or `KEY` when echoing to stdout (lines 64-67).

### `com.irs.mef.service.MefClientService`
`src/main/java/com/irs/mef/service/MefClientService.java` — the only holder of session state.

Session fields, lines 34-37: `currentSamlAssertion`, `currentSessionId`, `isLoggedIn`, `currentServiceContext`. Plain instance fields on a singleton bean — no synchronization, no per-caller isolation.

| Method | Line | Visibility |
|---|---|---|
| `init()` `@PostConstruct` | 40 | public |
| `login()` | 69 | public |
| `logout()` | 210 | public |
| `isLoggedIn()` | 250 | public |
| `getCurrentSamlAssertion()` | 257 | public — throws `NOT_LOGGED_IN` if no session |
| `getCurrentSessionId()` | 267 | public |
| `getCurrentServiceContext()` | 275 | public — throws `NOT_LOGGED_IN` if no session |
| `convertElementToString(Element)` | 285 | private |
| `extractSamlAssertionId(Element)` | 310 | private — `ID` attr, falls back to `AssertionID` |
| `testCertificate(boolean)` | 342 | public |
| `testKeystoreLoading()` | 397 | private (Level 1) |
| `testCertificateDetails()` | 452 | private (Level 2) |
| `testFullAuthentication()` | 534 | private (Level 3) |
| `parseDN(String)` | 573 | private |
| `cleanup()` `@PreDestroy` | 592 | public |

SDK classes via reflection:

| Line | Class |
|---|---|
| 79 | `gov.irs.mef.services.data.ETIN` |
| 80 | `gov.irs.a2a.mef.mefheader.TestCdType` (enum `P` for PRD, `T` for ATS — lines 92-94) |
| 81 | `gov.irs.mef.services.ServiceContext` — ctor `(ETIN, String asid, TestCdType)`, line 106-108 |
| 82 | `gov.irs.mef.services.msi.LoginClient` — `invoke(ServiceContext, File, String, String)`, lines 139-141 |
| 154 | `gov.irs.mef.services.SessionInfo` — `getSAMLToken()` returns `org.w3c.dom.Element` |
| 423, 461 | `gov.irs.mef.services.util.KeyStoreUtil` — static `loadKeyStore(File, char[])`, `listPrivateKeyAliases(KeyStore)`, `listKeyInfo(KeyStore)` |

`logout()` (lines 218-239) **calls no SDK service** — it clears the four session fields and returns `true`. `gov.irs.mef.services.msi.LogoutClient` is never referenced anywhere in `src/main`. The IRS-side session is left open, which is a plausible contributor to the "session limit" errors the retry logic exists to absorb.

### `com.irs.mef.service.SubmissionService`
`src/main/java/com/irs/mef/service/SubmissionService.java`

| Method | Line | Visibility |
|---|---|---|
| `submitSubmission(SubmitRequest)` | 39 | public |
| `createSubmissionArchive(String, String, String...)` | 197 | public — **stub**, returns a synthesized path (line 225), creates nothing |
| `getCertificateFile()` | 241 | private — **dead code**, no callers |
| `createIRSManifestReflection(...)` | 272 | private |

SDK classes via reflection (all in `submitSubmission`):

| Line | Class / call |
|---|---|
| 62 | `gov.irs.mef.services.ServiceContext` |
| 63 | `gov.irs.mef.services.transmitter.SendSubmissionsClient` — `invoke(ServiceContext, SubmissionContainer)` at 135-137 |
| 64 | `gov.irs.mef.inputcomposition.SubmissionXML` — in-memory ctor `(String name, String xml)` at 78-80 |
| 65 | `gov.irs.mef.inputcomposition.SubmissionManifest` — in-memory ctor `("manifest.xml", xml)` at 302-304 |
| 66 | `gov.irs.mef.inputcomposition.SubmissionBuilder` — static `createIRSSubmissionArchive` (93), `createPostmarkedSubmissionArchive` (109), `createSubmissionContainer` (125) |
| 67 | `gov.irs.mef.inputcomposition.SubmissionArchive` |
| 68 | `gov.irs.mef.inputcomposition.PostmarkedSubmissionArchive` |
| 69 | `gov.irs.mef.inputcomposition.SubmissionContainer` |
| 98 | `gov.irs.mef.inputcomposition.SubmissionBinaryAttachment` (array type only; `null` is passed at 105) |
| 140 | `gov.irs.mef.services.transmitter.SendSubmissionsResult` — `getDepositID()`, `getSubmissionReceiptList()` |
| 152 | `gov.irs.mef.SubmissionReceiptList` — `getCnt()`, `getReceiptBySubmissionId(String)` |

The manifest XML template is at lines 282-297: `IRSSubmissionManifest` with `SubmissionId`, `EFIN`, `GovernmentCd=IRS`, hardcoded `FederalSubmissionTypeCd=941`, optional `TaxPeriodBeginDt`/`TaxPeriodEndDt`, `TIN`. The receipt block (lines 151-166) fetches the receipt and only logs it — nothing from it reaches the response; `status` is hardcoded `"Accepted"` (line 148) and `messageId` is just the deposit ID (149).

### `com.irs.mef.service.StatusService`
`src/main/java/com/irs/mef/service/StatusService.java`

| Method | Line | Visibility |
|---|---|---|
| `getSubmissionStatus(String)` | 38 | public — body wrapped in `mefRetryTemplate.execute` at 48 |
| `getNewSubmissionsStatus()` | 149 | public — **stub**, returns an empty list (190-192) |
| `getCertificateFile()` | 206 | private — **dead code** |

SDK classes via reflection:

| Line | Class / call |
|---|---|
| 54 | `gov.irs.mef.services.ServiceContext` |
| 55 | `gov.irs.mef.services.transmitter.GetSubmissionStatusClient` — `invoke(ServiceContext, String)` at 63-64 |
| 67 | `gov.irs.mef.services.transmitter.GetSubmissionStatusResult` — `getStatusRecordList()` |
| 79 | `gov.irs.mef.StatusRecordList` — `getStatusRecords()` |
| 94-97 | Per-record getters, resolved off `statusRecord.getClass()`: `getSubmissionStatusTxt`, `getSubmsnStatusAcknowledgementDt`, `getDisclaimerTxt`, `getSubmissionId` |

Only the **first** status record is read (line 91). Session-limit detection is a substring match on the message and its first-level cause (lines 120-122).

### `com.irs.mef.service.AcknowledgementService`
`src/main/java/com/irs/mef/service/AcknowledgementService.java`

| Method | Line | Visibility |
|---|---|---|
| `getAcknowledgment(String ackId)` | 68 | public — retry-wrapped at 78 |
| `getNewAcknowledgments()` | 175 | public — retry-wrapped at 185, `maxCount = 100` at 199 |
| `getAcknowledgmentsBySubmission(String)` | 286 | public — retry-wrapped at 296; **GetNewAcks + client-side filter**, not a targeted query (comment at 279-281) |
| `buildAckResponseReflection(Object)` | 419 | private |
| `getCertificateFile()` | 594 | private — **dead code** |

SDK classes via reflection:

| Line | Class / call |
|---|---|
| 84, 191, 302 | `gov.irs.mef.services.ServiceContext` |
| 85 | `gov.irs.mef.services.transmitter.GetAckClient` — `invoke(ServiceContext, String)` at 93-94 |
| 97 | `gov.irs.mef.services.transmitter.GetAckResult` — `getAcknowledgementList()` |
| 109, 213, 325 | `gov.irs.mef.AcknowledgementList` — `getAcknowledgements()` |
| 192, 303 | `gov.irs.mef.services.transmitter.GetNewAcksClient` — `invoke(ServiceContext, Integer)` at 201-202 / 313-314 |
| 205, 317 | `gov.irs.mef.services.transmitter.GetNewAcksResult` — `getAcknowledgementList()`, `isMoreAvailableInd()` (230) |
| 334 | `gov.irs.mef.AcknowledgementList$Acknowledgement` — `getSubmissionId()` |

`buildAckResponseReflection` (419) uses a lambda `getField` (424-431) that swallows every reflection failure and returns `null`, then pulls ~25 getters: `getSubmissionId`, `getAcceptanceStatusTxt`, `getEFIN`/`getEIN`/`getTIN`, four `XMLGregorianCalendar` dates, five `BigInteger` amounts, `getTaxYr`, `getSubmissionTyp`, `getExtndSubmissionCategoryCd`, `getIRSSubmissionId`, `getReceiptId`, `getPaymentRequestRcvdCd`, plus `getValidationErrorList` / `getValidationAlertList` (474-533, per-error getters `getRuleNum`, `getSeverityCd`, `getErrorMessageTxt`, `getXpathContentTxt`, `getFieldValueTxt`).

Because `getField` cannot distinguish "getter absent" from "value null", a renamed SDK getter degrades silently to a null field rather than an error.

### `com.irs.mef.exception.MefException`
`src/main/java/com/irs/mef/exception/MefException.java` — extends `RuntimeException`; carries `errorCode` (constructors at 12, 18, 24, 30) and `detail`; getters at 36 and 40.

Error codes in use: `LOGIN_FAILED`, `LOGOUT_FAILED`, `NOT_LOGGED_IN`, `KEYSTORE_NOT_FOUND`, `CERTIFICATE_NOT_CONFIGURED`, `ASID_NOT_CONFIGURED`, `SAML_CONVERSION_ERROR`, `FILE_NOT_FOUND`, `SUBMISSION_FAILED`, `MANIFEST_CREATION_FAILED`, `ARCHIVE_CREATION_FAILED`, `STATUS_QUERY_FAILED`, `NO_STATUS_FOUND`, `ACK_NOT_FOUND`, `ACK_RETRIEVAL_FAILED`, `ACK_PARSE_ERROR`, `CONFIG_ERROR`, `VALIDATION_ERROR`.

### `com.irs.mef.exception.GlobalExceptionHandler`
`src/main/java/com/irs/mef/exception/GlobalExceptionHandler.java` — `@RestControllerAdvice`

| Handler | Line | Status |
|---|---|---|
| `handleMefException` | 25 | 500 |
| `handleIllegalArgumentException` | 44 | 400 |
| `handleGlobalException` | 64 | 500 |
| nested `ErrorResponse` DTO | 83 | `timestamp, status, errorCode, message, detail, path` |

Every `MefException` maps to 500, including `NOT_LOGGED_IN` and `FILE_NOT_FOUND`, which are client-side conditions.

### DTOs
`src/main/java/com/irs/mef/dto/` — all Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`.

| DTO | Lines | Fields |
|---|---|---|
| `LoginRequest` | 17-38 | etin, username, password, productionMode — **never bound by any controller** (auth endpoints are GET) |
| `LoginResponse` | 15-46 | success, samlAssertion, sessionId, timestamp, message, errorMessage |
| `SubmitRequest` | 18-66 | submissionId `@NotBlank` (23), submissionFilePath `@NotBlank` (29), productionMode, manifest, metadata, efin, tin, taxPeriodBegin, taxPeriodEnd (`LocalDate`) |
| `SubmitResponse` | 15-56 | submissionId, depositId, messageId, status, timestamp, message, errorMessage, accepted |
| `StatusResponse` | 15-52 | submissionId, status, statusCode, timestamp, description, details, ackAvailable |
| `AckResponse` | 25-176 | ackId, submissionId, ackType, timestamp, ackFilePath, errorCodes, errorMessages, details, efin, ein, tin, 5 amount fields, taxYear, submissionType, submissionCategoryCode, 3 timestamps, irsSubmissionId, receiptId, paymentRequestCode, hasValidationErrors, hasValidationAlerts |
| `AckResponse.AckListResponse` | 185 | acknowledgments, totalCount, message |
| `CertificateTestResponse` | 18-48 | success, timestamp, 3 nested results, errorMessage |
| ↳ `KeystoreLoadResult` / `CertificateDetailsResult` / `CertificateInfo` / `AuthenticationTestResult` | 54 / 66 / 79 / 94 | |

`SubmitResponse.errorMessage` and `AckResponse.errorCodes`/`errorMessages` are declared but never populated (`AcknowledgementService.java:544-545` sets them to `null` with a TODO).

---

## 3. REST API Surface

Base URL `http://localhost:8080/api` (`server.port: 8080`, `server.servlet.context-path: /api` in `application.yml:6-8`).

### `MefAuthController` — `@RequestMapping("/mef/auth")`

| Method | Path | Handler (line) | Request | Response |
|---|---|---|---|---|
| GET | `/mef/auth/login` | `login()` :39 | none — ETIN/ASID come from config | `LoginResponse` 200 |
| GET | `/mef/auth/logout` | `logout()` :55 | none | `Map<String,Object>` `{success, message}` 200 |
| GET | `/mef/auth/status` | `getSessionStatus()` :75 | none | `Map` `{loggedIn, samlAssertionId, sessionId, sessionType?, message}` 200 |
| GET | `/mef/auth/test-certificate` | `testCertificate(boolean)` :113 | `?fullAuthTest=false` | `CertificateTestResponse` 200 / 400 |

Login and logout are **GET**, not POST as older docs state, and take no body.

### `SubmissionController` — `@RequestMapping("/mef/submissions")`

| Method | Path | Handler (line) | Request | Response |
|---|---|---|---|---|
| POST | `/mef/submissions/submit` | `submitSubmission` :53 | `@Valid @RequestBody SubmitRequest` | `SubmitResponse` **201** |
| GET | `/mef/submissions/{submissionId}/status` | `getSubmissionStatus` :71 | path var | `StatusResponse` 200 |
| GET | `/mef/submissions/status/new` | `getNewSubmissionsStatus` :88 | none | `List<StatusResponse>` 200 — always empty (stub) |
| POST | `/mef/submissions/archive/create` | `createSubmissionArchive` :114 | `@RequestParam submissionId, returnXmlPath, attachments?` | `String` path 200 — stub |
| GET | `/mef/submissions/test/form941` | `testForm941Submission` :149 | none | `SubmitResponse` 201 |

`testForm941Submission` self-logs-in if needed (153-166), reads EFIN from config and validates `\d{6}` (173-185), builds a submission ID as `EFIN(6) + yyyyDDD(7) + millis-slice(7)` (198-208) and asserts `[0-9]{13}[a-z0-9]{7}` (211-215). **Line 225 hardcodes `/Users/ulugbekirmatov/Documents/MeF-test/test-scenarios/941-scenario-1-orchid-q1-2026/Return941-Scenario1.xml`** — that directory does not exist; the file actually lives under `.../Documents/MeF/test-scenarios/...` (`MeF-test` vs `MeF`). This endpoint fails with `FILE_NOT_FOUND` at `SubmissionService.java:50-53`. TIN is hardcoded `"003000004"` (239) and the tax period to Q1 2026 (242-243).

### `AcknowledgementController` — `@RequestMapping("/mef/acknowledgments")`

| Method | Path | Handler (line) | Request | Response |
|---|---|---|---|---|
| GET | `/mef/acknowledgments/{ackId}` | `getAcknowledgment` :32 | path var (really a **submission ID**, 20-char) | `AckResponse` 200 |
| GET | `/mef/acknowledgments/new` | `getNewAcknowledgments` :48 | none | `AckResponse.AckListResponse` 200 |
| GET | `/mef/acknowledgments/submission/{submissionId}` | `getAcknowledgmentsBySubmission` :65 | path var | `List<AckResponse>` 200 |

`/new` and `/{ackId}` overlap; Spring's literal-over-pattern precedence resolves `/new` correctly.

### Actuator
`management.endpoints.web.exposure.include: health,info` (`application.yml:130-137`) → `GET /api/actuator/health`, `/api/actuator/info`.

---

## 4. Runtime Data Flow

All four hops share one piece of state: the `ServiceContext` object created during login and held in `MefClientService.currentServiceContext` (line 37). Every downstream client is constructed fresh per call and handed that same context.

### Hop 1 — Login → SAML → ServiceContext

`MefAuthController.login()` :39 → `MefClientService.login()` :69

1. Read `etin` (71) and `asid` (98) from `MefSdkConfig.Authentication`.
2. Reflectively build `ETIN` (87), pick `TestCdType.T`/`P` from `mef.sdk.environment` (91-94).
3. Construct `ServiceContext(ETIN, asid, TestCdType)` (106-108).
4. Resolve keystore `File`, password, alias from `MefSdkConfig.Certificate`; verify the file exists (115-133).
5. `LoginClient.invoke(serviceContext, keystoreFile, keystorePassword, keyAlias)` (138-141). The SDK mutates `serviceContext` in place, attaching the session.
6. Read back `serviceContext.getSessionInfo()` (144-145), then `SessionInfo.getSAMLToken()` → `org.w3c.dom.Element` (155-156).
7. Serialize the element to a string (165 → `convertElementToString` :285) and pull the assertion `ID` attribute (168 → `extractSamlAssertionId` :310).
8. **Commit session state** (170-173): `currentSamlAssertion`, `currentSessionId` (the SAML assertion ID, or a `SESSION_<millis>` fallback), `currentServiceContext`, `isLoggedIn = true`.

The SAML string is stored but never read by any downstream service — it is exposed through `getCurrentSamlAssertion()` :257 and returned in `LoginResponse`, and that is all. The live credential is the `ServiceContext`.

### Hop 2 — SendSubmissions

`SubmissionController.submitSubmission` :53 (or `/test/form941` :149) → `SubmissionService.submitSubmission` :39

`isLoggedIn()` guard (43) → file existence check (49) → `mefClientService.getCurrentServiceContext()` (57) → build `SubmissionXML` from the file's text (75-80) → build `SubmissionManifest` (83-90 → :272) → `SubmissionBuilder.createIRSSubmissionArchive` (93-106) → `createPostmarkedSubmissionArchive` with `new GregorianCalendar()` (109-118) → 1-element array (121-122) → `createSubmissionContainer` (125-129) → `SendSubmissionsClient.invoke(serviceContext, container)` (135-137) → read `getDepositID()` (144) → `SubmitResponse` (171-179).

Not retry-wrapped — the only one of the four SDK calls without `mefRetryTemplate`.

### Hop 3 — GetSubmissionStatus

`SubmissionController.getSubmissionStatus` :71 → `StatusService.getSubmissionStatus` :38

`isLoggedIn()` guard (42) → `mefRetryTemplate.execute` (48) → `getCurrentServiceContext()` (51) → `GetSubmissionStatusClient.invoke(serviceContext, submissionId)` (63-64) → `getStatusRecordList()` (68-69) → `StatusRecordList.getStatusRecords()` (80-81) → first record only (91) → four getters (94-97) → `StatusResponse` (102-109). `ackAvailable` is a substring test for `Accept`/`Reject` (108).

### Hop 4 — GetNewAcks / GetAck

`AcknowledgementController.getNewAcknowledgments` :48 → `AcknowledgementService.getNewAcknowledgments` :175

`isLoggedIn()` guard (179) → `mefRetryTemplate.execute` (185) → `getCurrentServiceContext()` (188) → `GetNewAcksClient.invoke(serviceContext, 100)` (201-202) → `GetNewAcksResult.getAcknowledgementList()` (206-207) → `AcknowledgementList.getAcknowledgements()` (214-215) → `buildAckResponseReflection` per element (221 → :419) → `isMoreAvailableInd()` (230-231) → `AckListResponse` (234-240).

The single-ack path (`getAcknowledgment` :68) is the same shape but calls `GetAckClient.invoke(serviceContext, submissionId)` (93-94) and takes `acks.get(0)` (121). The by-submission path (:286) calls GetNewAcks and filters in Java (341-343).

**GetNewAcks is destructive on the IRS side** — acknowledgments are marked retrieved. `getAcknowledgmentsBySubmission` consumes the whole queue to find one ack and discards the rest without persisting them.

### Shared state summary

| Field | Set at | Read by |
|---|---|---|
| `currentServiceContext` | `MefClientService.java:172` | `SubmissionService:57`, `StatusService:51`, `AcknowledgementService:81/188/299` |
| `isLoggedIn` | 173 (true), 236 (false) | guards in all four services + `/mef/auth/status` |
| `currentSamlAssertion` | 170 | `LoginResponse` only |
| `currentSessionId` | 171 | `/mef/auth/status`, `/test/form941` logging |

No expiry tracking, no re-login on IRS session timeout, no mutex — a second concurrent `login()` overwrites the context that in-flight submissions are using. `@PreDestroy cleanup()` (:592) calls the no-op `logout()`.

---

## 5. Configuration Surface

### `application.yml` keys
`src/main/resources/application.yml`

| Key | Line | Default / value |
|---|---|---|
| `server.port` | 6 | 8080 |
| `server.servlet.context-path` | 8 | `/api` |
| `mef.sdk.toolkit-home` | 15 | `${A2A_TOOLKIT_HOME:classpath:mef_config}` — bound but unused by the SDK |
| `mef.sdk.environment` | 18 | `ATS` |
| `mef.sdk.transport.connect-timeout-seconds` / `read-timeout-seconds` | 22-23 | 600 / 600 |
| `mef.sdk.transport.http-chunking-enabled` / `http-chunk-size` | 24-25 | true / 8192 |
| `mef.sdk.audit.log-file` / `max-file-size-mb` | 29-30 | `logs/mef_audit_log.txt` / 10 |
| `mef.sdk.ssl.trust-store-*` | 35-37 | `${MEF_TRUSTSTORE_PATH:}`, `${MEF_TRUSTSTORE_PASSWORD:changeit}`, JKS |
| `mef.sdk.certificate.keystore-path` | 43 | `${MEF_KEYSTORE_PATH:}` |
| `mef.sdk.certificate.keystore-password` | 44 | `${MEF_KEYSTORE_PASSWORD:}` |
| `mef.sdk.certificate.keystore-type` | 45 | `${MEF_KEYSTORE_TYPE:PKCS12}` |
| `mef.sdk.certificate.key-alias` | 48 | `${MEF_KEY_ALIAS:}` |
| `mef.sdk.certificate.key-password` | 49 | `${MEF_KEY_PASSWORD:}` |
| `mef.sdk.certificate.use-windows-keystore` | 52 | false |
| `mef.sdk.authentication.etin` | 57 | `${MEF_ETIN:}` |
| `mef.sdk.authentication.efin` | 60 | `${MEF_EFIN:}` |
| `mef.sdk.authentication.username` / `password` | 63 / 66 | `${MEF_USERNAME:}` / `${MEF_PASSWORD:}` — bound, never used (cert-only auth) |
| `mef.sdk.authentication.asid` | 69 | `${MEF_ASID:}` |
| `mef.sdk.endpoints.ats.*` | 74-83 | base `https://la.alt.www4.irs.gov` + 8 paths |
| `mef.sdk.endpoints.prd.*` | 86-95 | base `https://www.irs.gov` + 8 paths |
| `mef.sdk.security.*` | 99-112 | RSA-SHA256, SHA256, exc-c14n, X509v3 token types |
| `logging.level.{com.irs.mef, gov.irs.mef}` | 118-119 | DEBUG |
| `logging.file.name` | 125 | `logs/mef-spring-boot.log` |
| `management.endpoints.web.exposure.include` | 134 | `health,info` |

The `mef.sdk.endpoints.*` and `mef.sdk.security.*` trees are bound into `MefSdkConfig` but never consulted at call time — the SDK resolves endpoints from `mef_config/config/*_endpoints.properties` and security from the XWSS XML files. Only `getBaseUrl()` reads them, and only for a startup log line (`MefClientService.java:43`).

### Environment variables

| Variable | Consumed at | Required |
|---|---|---|
| `MEF_ETIN` | `application.yml:57` | yes — login |
| `MEF_ASID` | 69 | yes — login (`ASID_NOT_CONFIGURED` at `MefClientService.java:542`) |
| `MEF_EFIN` | 60 | for `/test/form941` (`SubmissionController.java:173`) |
| `MEF_KEYSTORE_PATH` | 43 | yes |
| `MEF_KEYSTORE_PASSWORD` | 44 | yes |
| `MEF_KEYSTORE_TYPE` | 45 | no (PKCS12) |
| `MEF_KEY_ALIAS` | 48 | yes |
| `MEF_KEY_PASSWORD` | 49 | bound, never read by `login()` |
| `MEF_TRUSTSTORE_PATH` / `_PASSWORD` | 35-36 | optional |
| `MEF_USERNAME` / `MEF_PASSWORD` | 63 / 66 | unused |
| `A2A_TOOLKIT_HOME` | 15, and as a system property in `MefSpringBootApplication.java:40-46` | auto-set |

Resolution order (per `DotenvEnvironmentPostProcessor`): OS environment → `.env` in `user.dir` → `application.yml` defaults. `.env` is gitignored.

### `src/main/resources/mef_config/config/` — the A2A_TOOLKIT_HOME payload

| File | Purpose |
|---|---|
| `ats_endpoints.properties` | 25 service URLs on `la.alt.www4.irs.gov` (LOGIN, LOGOUT, SEND_SUBMISSIONS, GET_SUBMISSION_STATUS, GET_ACK, GET_NEW_ACKS, …) |
| `prd_endpoints.properties` | same 25 keys on `la.www4.irs.gov`. Note this is `la.www4`, while `application.yml:87` says `https://www.irs.gov` — the properties file is what the SDK actually uses |
| `login_client_security_config.xml` | XWSS profile for Login: signs the `MeFHeader` and SOAP body with an X.509 direct-reference token, RSA-SHA256 / SHA256 / exc-c14n; requires timestamp, username token, SV SAML assertion |
| `logout_client_security_config.xml` | XWSS for Logout: emits Timestamp + SV SAMLAssertion + UsernameToken, no signing |
| `basic_a2a_client_security_config.xml` | XWSS for all other A2A calls: Timestamp + SV SAMLAssertion + UsernameToken, plus the matching Require* assertions |
| `xwss_config.xsd` | Schema for the three XWSS files above |
| `transport.properties` | `connect.timeout.seconds=600`, `read.timeout.seconds=600`, `http.chunking.enabled=true`, `http.chunk.size=8192` — mirrors `mef.sdk.transport.*` |
| `audit_log.properties` | `audit_log_file=audit_log.txt`, `max_audit_file_size=1` (MB) — mirrors `mef.sdk.audit.*` |
| `logging.properties` | `java.util.logging` config, `.level = FINEST`, `FileHandler` |

The SDK appends `/config` to the toolkit home, so `A2A_TOOLKIT_HOME` must point at `mef_config`, not `mef_config/config` (`MefLoginIntegrationTest.java:45-47` documents this).

### `pom.xml` notables

| Aspect | Line | Value |
|---|---|---|
| Parent | 9-12 | `spring-boot-starter-parent` 3.2.0 |
| `java.version` / compiler source-target | 22 / 137-138 | 17 |
| `mef.sdk.home` property | 24 | `${project.basedir}/src/main/resources/mef_config` (declared, unreferenced elsewhere) |
| SDK dependency | 53-57 | `gov.irs.mef:mef-client-sdk:16.0` — **compile scope from the local repo, not `system` scope with a `lib/` path** |
| Metro/JAX-WS | 59-81 | `com.sun.xml.ws:webservices-{api,rt,extra,tools}:4.0.4` |
| XML security | 83-87 | `org.apache.santuario:xmlsec:4.0.2` |
| Other | 96-107 | `spring-retry` (version-managed), `io.github.cdimascio:dotenv-java:3.0.0` |
| JVM args for `spring-boot:run` | 123-128 | `--add-opens java.base/java.lang=ALL-UNNAMED`; `--add-exports` for `java.xml/…xerces.internal.dom`, `java.xml.crypto/…xml.internal.security`, `java.xml.crypto/org.jcp.xml.dsig.internal.dom` |
| Resource copy | 150-171 | copies `src/main/resources/mef_config` → `target/classes/mef_config` at the `validate` phase |

The five jars other than `mef_client_sdk.jar` carry real public coordinates and resolve from Maven Central. Only `gov.irs.mef:mef-client-sdk:16.0` must be installed by hand — and it is **not currently in `~/.m2/repository`** (`~/.m2` does not exist on this machine).

---

## 6. Build / Test / Run

### Toolchain prerequisites — not currently satisfied

On this machine (checked 2026-08-28), `mvn` is not on `PATH` and `/usr/bin/java` is the macOS stub (`Unable to locate a Java Runtime`). Neither a JDK nor Maven is installed, and `~/.m2` is absent. Nothing below can run until these are in place:

```bash
brew install openjdk@17 maven
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

### One-time: install the MeF SDK jar into the local repo

`pom.xml` expects `gov.irs.mef:mef-client-sdk:16.0` in `~/.m2`. The jar ships inside the toolkit zip and is gitignored, so it must be extracted and installed:

```bash
cd /Users/ulugbekirmatov/Documents/MeF
unzip -o "Version_16/A2A_Toolkit_Version16.0/MeF_Client_SDK/Java/dist/mef_client_sdk.zip" \
  -d /tmp/mef_sdk

mvn install:install-file \
  -Dfile=/tmp/mef_sdk/mef_client_sdk/lib/mef_client_sdk.jar \
  -DgroupId=gov.irs.mef -DartifactId=mef-client-sdk \
  -Dversion=16.0 -Dpackaging=jar
```

The zip also contains `webservices-{api,extra,rt,tools}-4.0.4.jar` and `xmlsec-4.0.2.jar`, which match the declared coordinates and will resolve from Central without manual installation.

### Build

```bash
cd /Users/ulugbekirmatov/Documents/MeF/mef-spring-boot-integration
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

mvn clean package -DskipTests     # → target/mef-spring-boot-integration-1.0.0.jar
mvn clean package                 # with tests
```

### Test

```bash
mvn test                                              # offline tests only (see gating caveat below)
mvn test -Dtest=Form941XmlGenerationTest              # XSD/field validation, no network
mvn test -Dtest=CertificateLoadingTest                # needs ./irs_cert/IRS_test_keystore.p12
mvn test -Dmef.integration.test.enabled=true \
         -Dtest=MefLoginIntegrationTest               # live IRS ATS login
mvn test -Dtest=Form941SubmissionTest                 # @SpringBootTest, live ATS submit
```

Gating: `MefLoginIntegrationTest` guards its live methods with `@EnabledIfSystemProperty(named="mef.integration.test.enabled", matches="true")` (lines 92, 314, 369, ~397); its offline methods (`testKeystoreExists` :66, `testLoadSdkClasses` :77, `testCreateLoginClient` :121, `testLoginClientInvokeMethods` :131, `testSdkKeystoreAccess` :183, `testKeystoreLoadingForSdk` :246) run unconditionally and expect `./irs_cert/IRS_test_keystore.p12` with password `test123`, alias `irs_test_cert` — hardcoded at lines 32-34; the file is not in the repo.

**`Form941SubmissionTest` is UNGATED and hits the live IRS ATS during a plain `mvn test`**: `test01_verifyConfiguration` :82, `test02_login` :117, `test03_submitForm941` :143, `test04_verifySubmissionResults` :192, `test05_getSubmissionStatus` :212, `test07_getAcknowledgmentsBySubmissionId` :338 (`test06_getNewAcknowledgments` commented out at 267-270). It hardcodes EFIN `238689` and processing date `2025331` (lines ~66-68).

`Form941XmlGenerationTest` resolves its XML as `user.dir/../test-scenarios/941-scenario-1-orchid-q1-2026/Return941-Scenario1.xml` (lines 35-37), correct relative to the repo layout.

### Run

```bash
cd /Users/ulugbekirmatov/Documents/MeF/mef-spring-boot-integration
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn spring-boot:run                 # JVM --add-opens/--add-exports come from pom.xml:123-128
```

Manual JAR launch needs the module flags supplied explicitly:

```bash
java \
  -DA2A_TOOLKIT_HOME=./src/main/resources/mef_config \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-exports java.xml/com.sun.org.apache.xerces.internal.dom=ALL-UNNAMED \
  --add-exports java.xml.crypto/com.sun.org.apache.xml.internal.security=ALL-UNNAMED \
  --add-exports java.xml.crypto/org.jcp.xml.dsig.internal.dom=ALL-UNNAMED \
  -jar target/mef-spring-boot-integration-1.0.0.jar
```

Drop the `-Djava.endorsed.dirs=./lib` that README.md:266 prescribes — `lib/` does not exist, and `java.endorsed.dirs` has been inert since Java 9 (the JVM errors on it in some configurations). The application sets it to the same nonexistent path at `MefSpringBootApplication.java:51-52`.

### Smoke test

```bash
curl http://localhost:8080/api/mef/auth/test-certificate            # levels 1-2, offline
curl "http://localhost:8080/api/mef/auth/test-certificate?fullAuthTest=true"   # + live login
curl http://localhost:8080/api/mef/auth/login
curl http://localhost:8080/api/mef/auth/status
curl http://localhost:8080/api/mef/acknowledgments/new
```

`./test-mef-login.sh` wraps build + run + curl; `./test-certificate-config.sh` checks `.env` and keystore setup.

Logs land in `logs/mef-spring-boot.log` (`application.yml:125`), `audit_log.txt` (per `audit_log.properties`, written to the working directory — `application.yml:29` says `logs/mef_audit_log.txt`, but the SDK reads the properties file, so the yml value has no effect), and `java.util.logging` output per `logging.properties`.

---

## 7. Known Defects & Documentation Drift

Documentation drift (older docs vs reality):

1. **No `lib/` directory, no system-scoped dependencies.** `pom.xml:53-87` uses ordinary local-repo coordinates. Every `-Djava.endorsed.dirs=./lib` instruction is stale.
2. **`sdk-reference/extracted/` holds 21 files, all under `META-INF/`** — the WSDL/XSD set. The 310 class files the exploration workflow depended on are absent (`*.class` gitignored); regenerate with `sdk-reference/scripts/generate-sdk-docs.sh`.
3. **Auth endpoints are GET, not POST**, and take no request body.
4. Undocumented endpoints exist: `GET /mef/auth/test-certificate`, `POST /mef/submissions/archive/create`, `GET /mef/submissions/test/form941`.

Defects worth acting on, roughly by severity:

5. **`/test/form941` is broken** — `SubmissionController.java:225` points at `Documents/MeF-test/...`; the file is at `Documents/MeF/test-scenarios/...`. A hardcoded absolute developer path in a controller regardless.
6. **`logout()` never contacts the IRS** (`MefClientService.java:218-239`). Sessions accumulate server-side until they time out — the most likely source of the "session limit" errors the retry layer was built to absorb. `LogoutClient` is unreferenced in `src/main`.
7. **The retry policy retries everything.** `RetryConfig.java:53-60` classifies `Exception.class` as retryable, so the `catch (MefException e) { throw e; }` "don't retry" blocks in `StatusService:111` and `AcknowledgementService:136/242/363` don't have their intended effect — `NOT_LOGGED_IN` and `ACK_NOT_FOUND` are retried three times across ~20 s of backoff.
8. **Session state is unguarded shared mutable state** on a singleton (`MefClientService.java:34-37`). Concurrent requests share one `ServiceContext`; a second login silently swaps the context out from under in-flight calls. No expiry tracking or re-login on IRS session timeout.
9. **`getAcknowledgmentsBySubmission` drains the ack queue.** GetNewAcks marks acknowledgments retrieved IRS-side; filtering client-side (`AcknowledgementService.java:341-343`) discards every non-matching ack permanently.
10. **`Form941SubmissionTest` submits to live IRS ATS during `mvn test`** with no gating property — add `@EnabledIfSystemProperty` for parity with `MefLoginIntegrationTest`.
11. Stubs presented as working endpoints: `StatusService.getNewSubmissionsStatus()` :149 always returns `[]`; `SubmissionService.createSubmissionArchive()` :197 returns a path to a file it never creates. Both reachable over HTTP.
12. Dead code: `getCertificateFile()` duplicated and unused in `SubmissionService:241`, `StatusService:206`, `AcknowledgementService:594`. `LoginRequest` bound by nothing. `MefSdkConfig.Authentication.username`/`password` and the entire `endpoints`/`security` trees never read at call time.
13. `buildAckResponseReflection`'s `getField` lambda (`AcknowledgementService.java:424-431`) swallows all reflection errors into `null` — an SDK getter rename degrades to silently-empty response fields.
14. Every `MefException` maps to HTTP 500 (`GlobalExceptionHandler.java:24-38`), including client-fault codes like `NOT_LOGGED_IN` (401/409) and `FILE_NOT_FOUND` (400).
15. PRD base URL disagrees between `application.yml:87` (`https://www.irs.gov`) and `prd_endpoints.properties` (`la.www4.irs.gov`). The properties file governs; the yml value is decorative but misleading.
