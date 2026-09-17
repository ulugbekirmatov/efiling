# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot application (Java 17, Spring Boot 3.2.0, Maven) integrating the IRS Modernized e-File (MeF) Client SDK v16 for Application-to-Application (A2A) e-filing of employment tax returns (Form 94x family, primarily 941). Main code lives in `mef-spring-boot-integration/`.

**Project phase (as of 2026-08):** The integration was originally built and proven against IRS ATS for **OneWell** (ETIN 97661 / EFIN 238689 / ASID 23868900 — all OneWell values, all being replaced). The project is now being re-implemented for **Pyramos Software LLC**, which holds the IRS **Reporting Agent** role and will e-file on behalf of *other companies*. That changes both the IRS enrollment (new EFIN/ETIN/ASID under Pyramos, Reporting Agent + Transmitter provider options) and the code (multi-tenant filers, `ReportingAgent` originator type, `ReportingAgentPINGrp` signature, per-client Form 8655 authorizations). See `CODE_MAP.md` for the code map and `mef-efiling-onboarding.html` for the end-to-end IRS onboarding guide.

**Pyramos onboarding status:** the IdenTrust IGC device certificate is already issued (`Pyramos Software Certificates/`: `O=Pyramos Software LLC, CN=c2s.pahealthmanagement.org`, issued by IGC Device CA 2, valid 2026-08-21 → 2027-08-21). The e-file application (EFIN/ETIN), Automated Enrollment (ASID + cert registration), keystore wiring, and ATS re-testing are still to do.

## Repository Layout

| Path | Contents |
|---|---|
| `mef-spring-boot-integration/` | The Spring Boot app |
| `c2s/` | C2S platform context: how to write backend functions (Nashorn ES5), queries (QIDs), and page scripts that call them. Start at `c2s/README.md` |
| `Version_16/A2A_Toolkit_Version16.0/` | IRS A2A Toolkit v16 — SDK JARs + IRS PDFs (Pub 5830, Strong Auth guide) |
| `Version_15/` | Older toolkit, reference only |
| `94x-2026/` | IRS 94x XSD schemas + business rules, 2026 Q1–Q4 (note quarterly version churn v1.0→v4.0) |
| `test-scenarios/` | ATS test scenario PDFs, hand-built Return XML, 941 XSDs (see its README) |
| `Pyramos Software Certificates/` | Pyramos IdenTrust cert material: private key, CSR, issued cert, CA chain |
| `Root.txt`, `SubCA1.txt` | IdenTrust Global Common Root CA 1 + IGC Device CA 2 chain |
| `create-pkcs12.sh`, `verify-pkcs12.sh` | Keystore assembly/verification (contain stale hardcoded paths — fix before use) |
| `mef-efiling-onboarding.html` | 7-phase IRS MeF onboarding guide (e-Services → e-file app → cert → AE → keystore → ATS → PRD) |
| `identrust-igc-certificate-guide.html` | IdenTrust IGC certificate deep-dive (CSR → PKCS12 → IRS registration) |

## Build, Run, Test

```bash
cd mef-spring-boot-integration
export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # Java 17 required; 8 too old, 21+/25 incompatible

mvn clean package               # build
mvn clean package -DskipTests   # build without tests
mvn spring-boot:run             # run (JVM flags + A2A_TOOLKIT_HOME are preconfigured in pom.xml)
mvn test -Dtest=Form941XmlGenerationTest          # single test class (offline)
./test-mef-login.sh                               # live ATS login probe (runs the app, GET /mef/auth/login; green 2026-09-16)
```

**⚠️ One switch arms two live tests:** `ReportingAgentForm941AtsTest` and `Form941SubmissionTest` are both gated behind the same `-Dmef.integration.test.enabled=true` (`MefLoginIntegrationTest` was deleted 2026-09-16; the login probe is `test-mef-login.sh`). Note the pom has **no surefire block**, so under `mvn test` neither `A2A_TOOLKIT_HOME` nor the `--add-opens` flags are set — these two tests cannot reach the SDK until that is added. Never set that property without a `-Dtest=<one class>` filter, or every one of them contacts IRS ATS in the same run. A plain `mvn test` without the property is offline-safe.

**⚠️ Build prerequisites (installed 2026-09-16 via `brew install openjdk@17 maven`; `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`):** the pom declares the SDK and Metro stack as ordinary Maven coordinates, but **five of them are not on Maven Central**: `gov.irs.mef:mef-client-sdk:16.0` and the four `com.sun.xml.ws:webservices-{api,rt,extra,tools}:4.0.4` jars. All five ship inside the SDK zip and must be installed into `~/.m2` by hand on a fresh machine (`org.apache.santuario:xmlsec:4.0.2` does resolve from Central). There is **no `lib/` directory** in the repo:

```bash
unzip -o "Version_16/A2A_Toolkit_Version16.0/MeF_Client_SDK/Java/dist/mef_client_sdk.zip" -d /tmp/mef_sdk
mvn install:install-file -Dfile=/tmp/mef_sdk/mef_client_sdk/lib/mef_client_sdk.jar \
  -DgroupId=gov.irs.mef -DartifactId=mef-client-sdk -Dversion=16.0 -Dpackaging=jar
for a in api rt extra tools; do
  mvn install:install-file -Dfile=/tmp/mef_sdk/mef_client_sdk/lib/webservices-$a-4.0.4.jar \
    -DgroupId=com.sun.xml.ws -DartifactId=webservices-$a -Dversion=4.0.4 -Dpackaging=jar
done
```

When running a packaged JAR manually, these JVM args are mandatory for Java 17 + Metro/JAX-WS:

```bash
java \
  -DA2A_TOOLKIT_HOME=./src/main/resources/mef_config \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-exports java.xml/com.sun.org.apache.xerces.internal.dom=ALL-UNNAMED \
  --add-exports java.xml.crypto/com.sun.org.apache.xml.internal.security=ALL-UNNAMED \
  --add-exports java.xml.crypto/org.jcp.xml.dsig.internal.dom=ALL-UNNAMED \
  -jar target/mef-spring-boot-integration-1.0.0.jar
```

XML validation without the app: `cd test-scenarios/schemas/941 && xmllint --noout --schema Return941.xsd ../../941-scenario-1-orchid-q1-2026/Return941-Scenario1.xml`

## Architecture

Standard layers under `src/main/java/com/irs/mef/`: `controller/` (REST), `service/` (SDK calls), `dto/`, `config/`, `exception/`. Server on `:8080`, context path `/api`.

**The central pattern — reflection for every SDK call.** All MeF SDK invocations go through `Class.forName("gov.irs.mef...")` + `getMethod().invoke()` to sidestep Java 17 module-access problems with the Metro stack. Follow this pattern for any new SDK operation (model: `MefClientService.login`).

**Session model.** `MefClientService` performs certificate-only login (`LoginClient.invoke(ctx, keystoreFile, keystorePassword, keyAlias)`), extracts the SAML token, and stores the **`ServiceContext` as a singleton instance field**. Every subsequent SDK call MUST reuse `getCurrentServiceContext()` — a fresh `ServiceContext` invalidates the IRS session. State is not thread-safe and has no expiry handling. `logout()` now calls `LogoutClient.invoke` by reflection and always clears local state afterward.

**Submission pipeline** (`SubmissionService`): return XML string → in-memory `SubmissionXML` → generated manifest XML → `SubmissionBuilder.createIRSSubmissionArchive` → `PostmarkedSubmissionArchive` → container → `SendSubmissionsClient.invoke` → Deposit ID.

**Config flow:** `MefSdkConfig` binds `mef.sdk.*` from `application.yml`; secrets come from env vars or a `.env` file loaded by `DotenvEnvironmentPostProcessor` (system env wins). SDK-owned config (endpoints, XWSS security, transport, logging) lives in `src/main/resources/mef_config/config/` and is selected via `A2A_TOOLKIT_HOME`.

### Implementation status (verified 2026-08-28)

Working against ATS: **Login** (`MefClientService.java:69-203`), **Logout** (`LogoutClient.invoke` via reflection, `MefClientService.java:215-255`), **SendSubmissions** (`SubmissionService.java:39-186`, manifest at `:272-311`), **GetSubmissionStatus** (`StatusService.java:38-142`), **GetNewAcks** (`AcknowledgementService.java:175-273`), **GetAck** (`AcknowledgementService.java:68-167` — its "ackId" param is really the submission ID), acks-by-submission via client-side filtering (`:286-394`), certificate diagnostics (`MefClientService.java:342-568`, `GET /mef/auth/test-certificate`).

Stubbed / broken — do not trust these endpoints: **GetNewSubmissionsStatus** (returns an empty list, `StatusService.java:149-199`), **createSubmissionArchive** (fabricates a path, `SubmissionService.java:197-234`). `/test/form941` now resolves the Orchid scenario XML relative to the repo root.

Other traps: `RetryConfig` retries **every** `Exception` (including `MefException`s like `NOT_LOGGED_IN`) despite in-service "don't retry" comments; `getAcknowledgmentsBySubmission` drains the IRS ack queue (GetNewAcks marks acks retrieved server-side) and discards non-matching acks; every `MefException` maps to HTTP 500. Full defect list and per-class map with verified line numbers: `CODE_MAP.md` §7.

## Hard-Won Gotchas (do not rediscover these)

1. **Submission ID:** exactly 20 chars, `[0-9]{13}[a-z0-9]{7}` = 6-digit EFIN + **`yyyyDDD` (year + day-of-year)** + 7 lowercase alphanumerics. The date must be the **current processing year**, not the tax-period year (`MEF00004` otherwise). `SUBMISSION_ID_FORMAT.md` is the authority; `FORM_941_SUBMISSION_IMPLEMENTATION.md` still teaches a superseded `YYYYmDD` format — ignore that part. Correct live code: `SubmissionController.java:206-211`.
2. **In-memory SDK objects only:** file-based `SubmissionXML`/`SubmissionManifest` constructors break serialization (`InMemoryMethodOnFileBasedInstanceException`). Always `new SubmissionXML(filename, contentString)`.
3. **Manifest is mandatory** (`MeFClientSDK000004` if null): SubmissionId, EFIN, GovernmentCd, FederalSubmissionTypeCd, TaxPeriodBeginDt/EndDt, TIN. `FederalSubmissionTypeCd` is currently hardcoded to `941` (`SubmissionService.java:288`).
4. **EFIN ≠ ETIN** and one submission uses both: ETIN authenticates the A2A session *and* fills the manifest `<EFIN>` tag (despite the name); EFIN prefixes the submission ID and goes in the return XML `OriginatorGrp`. See `EFIN_ETIN_USAGE.md`.
5. **Status/ack payloads arrive as MTOM ZIP attachments**, not in the SOAP body — the SDK unpacks them.
6. **IRS needs ~2–5 minutes** after submission before status/acks exist; earlier queries return `NO_STATUS_FOUND`/empty.
7. **Debugging:** IRS errors surface as generic `ErrorExceptionDetail`; the real SOAP fault is only in `a2a_sdk.log.*` (project root, rotating, level FINEST). App log: `logs/mef-spring-boot.log`.
8. **Ack data quality:** EFIN may return `999999`, TIN/amounts often null — **EIN is the reliable identifier** for employer returns.
9. `sdk-reference/extracted/` contains **no `.class` files on a fresh clone** (gitignored) — only WSDLs survive. Regenerate with `sdk-reference/scripts/generate-sdk-docs.sh` before relying on it.

## Configuration

Env vars (or `.env`, gitignored): `MEF_ETIN`, `MEF_EFIN`, `MEF_ASID`, `MEF_KEYSTORE_PATH`, `MEF_KEYSTORE_PASSWORD`, `MEF_KEYSTORE_TYPE` (default `PKCS12`), `MEF_KEY_ALIAS`, `MEF_KEY_PASSWORD`, optional `MEF_TRUSTSTORE_PATH`/`MEF_TRUSTSTORE_PASSWORD`, `A2A_TOOLKIT_HOME`. `MEF_USERNAME`/`MEF_PASSWORD` are declared but unused — authentication is certificate-only. No `.env` currently exists; recreate it with Pyramos values once issued.

Environment selection: `mef.sdk.environment` = `ATS` (test, `la.alt.www4.irs.gov`) or `PRD`. Known inconsistency: `application.yml` lists PRD as `www.irs.gov` while the SDK's `prd_endpoints.properties` uses `la.www4.irs.gov` — the SDK properties win; reconcile before production.

## SDK Reference

Consult before implementing any SDK operation — never guess SDK APIs:
- `mef-spring-boot-integration/sdk-reference/docs/` — `SDK_API_REFERENCE.md`, `SDK_QUICK_REF.md`, `SDK_WSDL_GUIDE.md`
- `sdk-reference/extracted/META-INF/wsdl/` — authoritative WSDLs (`MeFTransmitterServicesMTOM.wsdl`, `MeFMSIServices.wsdl`)
- `javap -classpath <path-to-mef_client_sdk.jar> -public gov.irs.mef.services.transmitter.SendSubmissionsClient` for exact signatures

Key packages: `gov.irs.mef.services.msi.*` (Login/Logout), `gov.irs.mef.services.transmitter.*` (Submit/Status/Ack), `gov.irs.mef.services.data.*`, `gov.irs.a2a.mef.mefheader.*`.

## Pyramos Reporting Agent Work (current focus)

What must change relative to the OneWell build:

1. **New IRS identifiers** — issued (2026-09): EFIN 102192, ETINs 44753 (Transmitter, Production type), 44754 (Software Developer, Test — use this one for ATS), 44762 (Online Provider); ASID 10219201 (active, both ETINs attached); Reporting Agent PIN issued; Software ID **not yet issued** (software-package questionnaire pending). Values live in `mef-spring-boot-integration/.env` (gitignored). ⚠️ **EFIN 102192 starts with 10**, and rule R0000-118-01 forces `OriginatorTypeCd=OnlineFiler` for EFINs starting 10/21/32/44/53 — a `ReportingAgent` return under this EFIN is rejected; resolution pending with e-Help. OneWell values (97661 / 238689 / 23868900) purged from `test-mef-login.sh`, `Form941SubmissionTest.java` and `SubmissionController.java` on 2026-09-16; `MefLoginIntegrationTest.java` (OneWell values, nonexistent keystore path, direct SDK imports) was deleted the same day; the live login probe is `test-mef-login.sh` against the running app, green against ATS on 2026-09-16 (SAML for ASID 10219201). EIN 003000004 is Orchid, the IRS ATS scenario-1 employer, not a OneWell value; it stays.
2. **Return header changes** (see `94x-2026/.../ReturnHeader94x.xsd`): `OriginatorTypeCd` = `ReportingAgent` (currently `OnlineFiler`), replace `OnlineFilerPINGrp` with `ReportingAgentPINGrp` (`PIN`, `RAPINEnteredByCd=REPORTING AGENT`, `JuratDisclosureCd=REPORTING AGENT PIN`), add `ReportingAgent94XFilerGrp` identifying Pyramos alongside the per-client `<Filer>`.
3. **Multi-tenancy**: per-client EIN/name/address/tax period in submissions, a client model with Form 8655 authorization status, and persistence of submission ↔ deposit ID ↔ status ↔ acknowledgment per client (IRS retention requirement). Today there is no persistence and `SubmitRequest` has no tenant field.
4. **Session hygiene**: `LogoutClient` is wired. Session expiry/refresh and a mutex around the singleton `ServiceContext` are still required at Reporting Agent volume.
5. **Certificate**: build the PKCS12 from `Pyramos Software Certificates/` (cert + SubCA1 + Root chain), register it in Automated Enrollment against the new ASID. Note both the old OneWell cert and the new Pyramos cert share `CN=c2s.pahealthmanagement.org` — only the `O=` differs; don't mix them up. Cert renewal due before 2027-08-21.

## Documentation Authority Notes

- `SUBMISSION_ID_FORMAT.md` — correct (day-of-year format). Overrides the submission-ID section of `FORM_941_SUBMISSION_IMPLEMENTATION.md`.
- `STATUS_AND_ACK_SERVICES_DOCUMENTATION.md` — SOAP request/response specs for status/ack services.
- `EFIN_ETIN_USAGE.md` — the EFIN/ETIN placement map.
- `PROJECT_SUMMARY.md` and `SETUP-COMPLETE.md` — stale historical snapshots (OneWell-era, wrong paths); do not follow them.
