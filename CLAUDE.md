# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot application (Java 17, Spring Boot 3.2.0, Maven) integrating the IRS Modernized e-File (MeF) Client SDK v17 (WSDL 10.A) for Application-to-Application (A2A) e-filing of employment tax returns (Form 94x family, primarily 941). Main code lives in `mef-spring-boot-integration/`.

**Project phase (as of 2026-08):** The integration was originally built and proven against IRS ATS for **OneWell** (ETIN 97661 / EFIN 238689 / ASID 23868900 — all OneWell values, all being replaced). The project is now being re-implemented for **Pyramos Software LLC**, which holds the IRS **Reporting Agent** role and will e-file on behalf of *other companies*. That changes both the IRS enrollment (new EFIN/ETIN/ASID under Pyramos, Reporting Agent + Transmitter provider options) and the code (multi-tenant filers, `ReportingAgent` originator type, `ReportingAgentPINGrp` signature, per-client Form 8655 authorizations). See `CODE_MAP.md` for the code map and `mef-efiling-onboarding.html` for the end-to-end IRS onboarding guide.

**Pyramos onboarding status:** the IdenTrust IGC device certificate is already issued (`Pyramos Software Certificates/`: `O=Pyramos Software LLC, CN=c2s.pahealthmanagement.org`, issued by IGC Device CA 2, valid 2026-08-21 → 2027-08-21). The e-file application (EFIN/ETIN), Automated Enrollment (ASID + cert registration), keystore wiring, and ATS re-testing are still to do.

## Repository Layout

| Path | Contents |
|---|---|
| `mef-spring-boot-integration/` | The Spring Boot app |
| `c2s/` | C2S platform context: how to write backend functions (Nashorn ES5), queries (QIDs), and page scripts that call them. Start at `c2s/README.md` |
| `Version_17/A2A_Toolkit_Version17.0/` | IRS A2A Toolkit v17 (WSDL 10.A), the SDK the pom builds against. Only `MeF_Client_SDK/Java/dist/mef_client_sdk.zip` and the release notes are tracked; the rest of the 626 MB zip is gitignored |
| `Version_16/A2A_Toolkit_Version16.0/` | IRS A2A Toolkit v16 (WSDL 10.9), superseded 2026-09-30. v17's Java SDK differs only in the `WSDL_VERSION`/`TOOLKIT_VERSION` constants and `MeFHeader.xsd` `WSDLVersionNum` fixed value |
| `test-scenarios/ats-ty2026/` | TY2026 94x ATS scenarios 1-4. Each scenario-<id>-<slug> directory holds the IRS PDF, `client-return-body.xml`, and `line-mapping.tsv` |
| `test-scenarios/ats-ty2026-annual/` | TY2026 Form 940 ATS scenarios `940-1`..`940-3`. Element names are unverified guesses until the 2026v2.0 schema lands |
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
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home   # maven-enforcer fails the build on any other JDK

mvn clean package               # build
mvn clean package -DskipTests   # build without tests
mvn spring-boot:run             # run (JVM flags + A2A_TOOLKIT_HOME are preconfigured in pom.xml)
mvn test -Dtest=AtsScenarioComposeTest            # single test class (offline)
./mef check                                       # offline: doctor, mvn test, npm test, next build
./mef scenarios                                   # list scenario id, form, title
./mef preflight <id>                              # offline preflight; never logs in
./mef ats <id>                                    # LIVE: send one scenario to ATS
./mef login                                       # LIVE: login probe, no send
```

**Live IRS tests:** a class that logs in to IRS carries `@LiveIrsTest` (`src/test/java/com/irs/mef/live/`) and runs only when `-Dmef.live.test=<its simple class name>`, so one flag arms one class. `LiveIrsTestGuardTest` fails `mvn test` if a test calls `.probe(` or `.login(` without the annotation or uses the retired `mef.integration.test.enabled` switch. Send one ATS scenario with `./mef ats <id>` (`AtsRunTest`, no default scenario). Log in with `./mef login` (`IrsLoginProbeTest`). A plain `mvn test` is offline-safe. CI (`.github/workflows/test.yml`) runs `mvn -B test` and, in `c2s-web/`, `npm test` plus `npx next build` on every push and PR.

**⚠️ Build prerequisites (installed 2026-09-16 via `brew install openjdk@17 maven`; `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`):** the pom declares the SDK and Metro stack as ordinary Maven coordinates, but **five of them are not on Maven Central**: `gov.irs.mef:mef-client-sdk:17.0` and the four `com.sun.xml.ws:webservices-{api,rt,extra,tools}:4.0.4` jars. All five ship inside the SDK zip; install them into `~/.m2` once per machine with `mef-spring-boot-integration/install-sdk-jars.sh` (CI runs the same script, `.github/workflows/test.yml`). `org.apache.santuario:xmlsec:4.0.2` does resolve from Central. There is **no `lib/` directory** in the repo.

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

**Session model.** `IrsSession` performs certificate-only login (`LoginClient.invoke(ctx, keystoreFile, keystorePassword, keyAlias)`), extracts the SAML token, and stores the **`ServiceContext` as a singleton instance field**. Every subsequent SDK call MUST reuse `getCurrentServiceContext()` — a fresh `ServiceContext` invalidates the IRS session. State is not thread-safe and has no expiry handling. `logout()` now calls `LogoutClient.invoke` by reflection and always clears local state afterward.

**The one send path.** `POST /api/mef/filings` (`FilingController`) → `FilingDesk` → `IrsWire` (SDK `SendSubmissionsClient`, reusing the `IrsSession` session). The body is a client return; `FilingDesk` composes it. The manifest comes from `ManifestXml`, the ID from `SubmissionId.mint`, the return XML from `com.irs.mef.filing`. Read: `GET /api/mef/filings/{submissionId}`.

**Config flow:** `MefSdkConfig` binds `mef.sdk.*` from `application.yml`; secrets come from env vars or a `.env` file loaded by `DotenvEnvironmentPostProcessor` (system env wins). SDK-owned config (endpoints, XWSS security, transport, logging) lives in `src/main/resources/mef_config/config/` and is selected via `A2A_TOOLKIT_HOME`.

### Implementation status (verified 2026-10-27)

The legacy send/status/ack generation (`SubmissionController`, `AcknowledgementController`, `SubmissionService`, `StatusService`, their DTOs, and the GetNewAcks methods of `AcknowledgementService`) was deleted in commit 6e0bb7e, together with its stubs (`GetNewSubmissionsStatus`, `createSubmissionArchive`) and the `/test/form941` route.

What exists now: **Login/Logout/status/certificate diagnostics** at `POST /api/mef/session/login`, `POST /api/mef/session/logout`, `GET /api/mef/session`, and `GET /api/mef/session/certificate` in `SessionController`, backed by `IrsSession`; **send** through the new path above; **acknowledgments** fetched by `IrsSession.getAck` (through `IrsWire`) from the inspector route `POST /api/inspector/sends/{submissionId}/ack` and stored in `data/ack-store/` (`FileAckStore`). The server-rendered inspector page is deleted.

Other traps: `RetryConfig` retries **every** `Exception` (including `MefException`s like `NOT_LOGGED_IN`) despite in-service "don't retry" comments; every `MefException` maps to HTTP 500. `CODE_MAP.md` predates 6e0bb7e, so its sections on the deleted classes are historical.

## Hard-Won Gotchas (do not rediscover these)

1. **Submission ID:** exactly 20 chars, `[0-9]{13}[a-z0-9]{7}` = 6-digit EFIN + **`yyyyDDD` (year + day-of-year)** + 7 lowercase alphanumerics. The date must be the **current processing year**, not the tax-period year (`MEF00004` otherwise). `SUBMISSION_ID_FORMAT.md` is the authority; `FORM_941_SUBMISSION_IMPLEMENTATION.md` still teaches a superseded `YYYYmDD` format — ignore that part. Live code: `SubmissionId.mint`.
2. **In-memory SDK objects only:** file-based `SubmissionXML`/`SubmissionManifest` constructors break serialization (`InMemoryMethodOnFileBasedInstanceException`). Always `new SubmissionXML(filename, contentString)`.
3. **Manifest is mandatory** (`MeFClientSDK000004` if null): SubmissionId, EFIN, GovernmentCd, FederalSubmissionTypeCd, TaxPeriodBeginDt/EndDt, TIN. `ManifestXml` writes all of them; `FederalSubmissionTypeCd` comes from `FormSpec`, not a constant.
4. **One EFIN, three places; the ETIN only logs in.** The originator EFIN prefixes the submission ID, fills the manifest `<EFIN>`, and goes in `OriginatorGrp`. R0000-054-01 rejects a manifest EFIN that differs from `OriginatorGrp`. The id, the manifest, and `OriginatorGrp` all read one `Originator`, so the mismatch cannot be built. The ETIN authenticates the A2A session and nothing else. The old two-number model (ETIN in the manifest) was wrong, and its doc is deleted.
5. **Status/ack payloads arrive as MTOM ZIP attachments**, not in the SOAP body — the SDK unpacks them.
6. **IRS needs ~2–5 minutes** after submission before status/acks exist; earlier queries return `NO_STATUS_FOUND`/empty.
7. **Debugging:** IRS errors surface as generic `ErrorExceptionDetail`; the real SOAP fault is only in `a2a_sdk.log.*` (project root, rotating, level FINEST). App log: `logs/mef-spring-boot.log`.
8. **Ack data quality:** EFIN may return `999999`, TIN/amounts often null — **EIN is the reliable identifier** for employer returns.
9. `sdk-reference/extracted/` contains **no `.class` files on a fresh clone** (gitignored) — only WSDLs survive. Regenerate with `sdk-reference/scripts/generate-sdk-docs.sh` before relying on it.

## Configuration

Env vars (or `.env`, gitignored): `MEF_ETIN`, `MEF_EFIN`, `MEF_ASID`, `MEF_KEYSTORE_PATH`, `MEF_KEYSTORE_PASSWORD`, `MEF_KEYSTORE_TYPE` (default `PKCS12`), `MEF_KEY_ALIAS`, `MEF_KEY_PASSWORD`, `MEF_SOFTWARE_ID` (94x quarterly package), `MEF_SOFTWARE_ID_ANNUAL` (94x annual package, 940; IRS issues it separately), optional `MEF_TRUSTSTORE_PATH`/`MEF_TRUSTSTORE_PASSWORD`, `A2A_TOOLKIT_HOME`. `MEF_USERNAME`/`MEF_PASSWORD` are declared but unused — authentication is certificate-only. No `.env` currently exists; recreate it with Pyramos values once issued.

Environment selection: `mef.sdk.environment` = `ATS` (test, `la.alt.www4.irs.gov`) or `PRD`. Known inconsistency: `application.yml` lists PRD as `www.irs.gov` while the SDK's `prd_endpoints.properties` uses `la.www4.irs.gov` — the SDK properties win; reconcile before production.

## SDK Reference

Consult before implementing any SDK operation — never guess SDK APIs:
- `mef-spring-boot-integration/sdk-reference/docs/` — `SDK_API_REFERENCE.md`, `SDK_QUICK_REF.md`, `SDK_WSDL_GUIDE.md`
- `sdk-reference/extracted/META-INF/wsdl/` — WSDLs (`MeFTransmitterServicesMTOM.wsdl`, `MeFMSIServices.wsdl`). Extracted from an older SDK: v17's WSDLs are byte-identical, but its `MeFHeader.xsd` pins `WSDLVersionNum` to 10.A, not 10.9
- `javap -classpath <path-to-mef_client_sdk.jar> -public gov.irs.mef.services.transmitter.SendSubmissionsClient` for exact signatures

Key packages: `gov.irs.mef.services.msi.*` (Login/Logout), `gov.irs.mef.services.transmitter.*` (Submit/Status/Ack), `gov.irs.mef.services.data.*`, `gov.irs.a2a.mef.mefheader.*`.

## Pyramos Reporting Agent Work (current focus)

What must change relative to the OneWell build:

1. **New IRS identifiers** — issued (2026-09): EFIN 102192, ETINs 44753 (Transmitter, Production type), 44754 (Software Developer, Test — use this one for ATS), 44762 (Online Provider); ASID 10219201 (active, both ETINs attached); Reporting Agent PIN issued; TY2026 94x quarterly Software ID 26996214 (on the accepted 2026-09-25 send; TY2027 needs a new `2799NNNN` ID, Pub 5078 §2.4). Values live in `mef-spring-boot-integration/.env` (gitignored). ⚠️ **EFIN 102192 starts with 10**, and rule R0000-118-01 forces `OriginatorTypeCd=OnlineFiler` for EFINs starting 10/21/32/44/53 — a `ReportingAgent` return under this EFIN is rejected, so `Originator` refuses these prefixes; sends use the non-online EFIN 237861. OneWell values (97661 / 238689 / 23868900) purged from the retired login script, `Form941SubmissionTest.java` and `SubmissionController.java` on 2026-09-16; `MefLoginIntegrationTest.java` (OneWell values, nonexistent keystore path, direct SDK imports) was deleted the same day; the live login probe is `./mef login`, green against ATS on 2026-09-16 (SAML for ASID 10219201). EIN 003000004 is Orchid, the IRS ATS scenario-1 employer, not a OneWell value; it stays.
   **ATS status (2026-10-14):** all four TY2026 94x quarterly scenarios **Accepted** under EFIN 237861 (the non-online EFIN in `.env`): scenario 1 Orchid 941 `2378612026268yagqidh` (2026-09-25); scenario 2 Marigold 941 + Schedule B `2378612026287enq7gwn`, scenario 3 Daffodil CPEO 941 + Schedule R + Form 8974 `237861202628741tq062`, scenario 4 Orchid 941-X `2378612026287svv97c5` (all 2026-10-14, first try). Acks are stored in `mef-spring-boot-integration/data/ack-store/`. Pub 5078 §3.5.1: one passed 941 scenario earns 94x quarterly Pass; e-Help (866-255-0654) records it.
   **Annual (Form 940):** a quarterly pass does not cover 940/943/944/945; call e-Help to open annual testing. Annual returns use returnVersion `2026v2.0` (ATS from 2026-11-01; `2026v1.0` is ATS-only and expires 2026-10-31), header `TaxYr` instead of `QuarterEndingDt`. `SchemaRelease` owns version + bundle per form. `FormSpec.F940` is `SchemaBinding.Pending` until the 2026v2.0 bundle (e-Services SOR mailbox) is copied verbatim to `src/main/resources/schemas/94x/2026v2.0/`. Preflight refuses that pending form with its reason, and `AtsScenarioComposeTest.anAnnual940ReturnIsSchemaValid` skips with the same text. Correct the guessed element names in the three 940 fixtures, switch `F940` to `Bundled`, then regenerate snapshots (`-Dats.snapshots.update=true`).
2. **Return header changes** (see `94x-2026/.../ReturnHeader94x.xsd`): `OriginatorTypeCd` = `ReportingAgent` (currently `OnlineFiler`), replace `OnlineFilerPINGrp` with `ReportingAgentPINGrp` (`PIN`, `RAPINEnteredByCd=REPORTING AGENT`, `JuratDisclosureCd=REPORTING AGENT PIN`), add `ReportingAgent94XFilerGrp` identifying Pyramos alongside the per-client `<Filer>`.
3. **Multi-tenancy**: per-client EIN/name/address/tax period in submissions, a client model with Form 8655 authorization status, and persistence of submission ↔ deposit ID ↔ status ↔ acknowledgment per client (IRS retention requirement). Today there is no persistence and `SubmitRequest` has no tenant field.
4. **Session hygiene**: `LogoutClient` is wired. Session expiry/refresh and a mutex around the singleton `ServiceContext` are still required at Reporting Agent volume.
5. **Certificate**: build the PKCS12 from `Pyramos Software Certificates/` (cert + SubCA1 + Root chain), register it in Automated Enrollment against the new ASID. Note both the old OneWell cert and the new Pyramos cert share `CN=c2s.pahealthmanagement.org` — only the `O=` differs; don't mix them up. Cert renewal due before 2027-08-21.

## Documentation Authority Notes

- `SUBMISSION_ID_FORMAT.md` — correct (day-of-year format). Overrides the submission-ID section of `FORM_941_SUBMISSION_IMPLEMENTATION.md`.
- `STATUS_AND_ACK_SERVICES_DOCUMENTATION.md` — SOAP request/response specs for status/ack services.

## Environment & Artifacts

- Save patches, SQL files, and reports to the repo root or `reports/`, never only to the session scratchpad. Always print the absolute path.
- Before writing a migration or phase script, confirm which rollout phases have actually run (DDL, shadow, reconcile, and so on) and whether the target tables hold data.
- Basecamp close-out: check for an existing todo before creating one, to avoid duplicates. If DNS or the API fails, output the todo text so it can be added manually.
- Before debugging an IRS reject or SDK error, search the repo for its code (`git grep R0000-118`, `X0000-008`) first. Past fixes live in `x0000-008-investigation-2026-09-16.html`, `test-scenarios/README.md` (Namespace Issues), and `.audit/`.

## Rules and what enforces them

When the operator corrects you, fix the mistake and add a row here. If the row already exists and nothing enforces it, that is a repeat: move it up a level (architecture, type, check) in the same change. Drop a row once its mistake cannot happen.

| Rule | Enforced by |
|---|---|
| A test that logs in to IRS runs only alone, armed by its own class name | `@LiveIrsTest` + `LiveIrsTestGuardTest` (fails on `.login(` without the annotation, or on the retired `mef.integration.test.enabled`) |
| `mvn test` stays green and offline | `.github/workflows/test.yml` runs it on every push and PR |
| Build and test on Java 17 only | `maven-enforcer-plugin` in `pom.xml` (`[17,18)`) |
| Text files use LF; vendor drops and captures stay byte-exact | `.gitattributes` |
| No OneWell identifiers (97661 / 238689 / 23868900) in new code or scripts | `RepoGuardTest.oneWellIdentifiersDoNotSpread` (per-file ratchet; line exceptions need `reason`, `expires`, `approved-by`) |
| Superseded docs stay deleted | `RepoGuardTest.retiredDocsStayDeleted` |
| Reporting Agent EFIN must not start 10/21/32/44/53 (R0000-118-01); filing IP must be a routable IPv4 (R0000-244) | `Originator` constructor + `OriginatorTest` |
| One EFIN in submission ID, manifest, and `OriginatorGrp` (R0000-054-01) | `ManifestXmlTest` |
| Submission ID is `EFIN + yyyyDDD + 7 [a-z0-9]` | `SubmissionId` (the only send path builds it there; the legacy inline builder was deleted in 6e0bb7e) |
| Never visit the TaxBandits website (taxbandits.com, any subdomain), by any tool or delegate; use IRS/SSA primary sources | `~/.claude/hooks/taxbandits-gate.sh` (PreToolUse deny on WebFetch, WebSearch, browser navigation, and shell fetches naming the domain) plus `permissions.deny` WebFetch rules in `~/.claude/settings.json`. A grok/Cursor brief is not gated: docs only |
| `next build` must not break a running `next dev` | `c2s-web/next.config.js` picks `distDir` by phase (`.next` for dev, `.next-build` for build/start), so they share nothing |
| Personal identifiers and session credentials are masked in one place before anything reaches the browser; a sibling repo's script is loaded through one helper | `c2s-web/app/lib/redact.guard.test.js` fails on a second copy outside `c2s-web/app/lib/redact.js` or `c2s-web/app/lib/nodeRequire.js`; each catalog's boundary test asserts its raw SSNs are absent from the payload |
| `npm test` and `npx next build` in `c2s-web` stay green | `.github/workflows/test.yml` job `c2s-web-test` on every push and PR; `node --test` discovery, so a new test file needs no list entry |
| Search the repo for a reject code before debugging it | Docs only (judgment) |
| Reports go to the repo root or `reports/`; migrations check rollout phases first; Basecamp todos are deduped | Docs only (judgment) |

