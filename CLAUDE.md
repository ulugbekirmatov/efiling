Act with ./mef; every verb except ats, login, and checkbook is offline and safe to repeat.

Spring Boot 3.2 on Java 17 that e-files Form 94x returns to the IRS Modernized e-File (MeF) system through the A2A Client SDK v17 (WSDL 10.A), as Pyramos Software LLC, a Reporting Agent filing for other companies. The Java app is `mef-spring-boot-integration/`; `c2s-web/` is the operator UI; `c2s/` explains the C2S platform (start at `c2s/README.md`); `test-scenarios/` holds the ATS scenarios; `94x-2026/` holds IRS schemas and business rules; `Version_17/` holds the SDK zip (`mef-spring-boot-integration/install-sdk-jars.sh` puts its five jars into `~/.m2`).

## Commands

```
./mef check              offline suite. Safe to repeat. Refuses a JDK other than 17.
./mef scenarios          id, form, title
./mef preflight <id>     offline compose and validate. Never logs in.
./mef pdf <id>           offline paper copy on the IRS forms. <id> is a scenario or a captured submission id.
./mef ats <id>           LIVE send to ATS. Exit 0 only when the ack is Accepted.
./mef login              LIVE login probe, no send.
./mef checkbook          LIVE Checkbook sandbox: one $1.00 digital check, then read it back.
```

## Where facts live

| Fact | File |
|---|---|
| EFIN, PIN, software IDs, filing IP | `mef-spring-boot-integration/src/main/java/com/irs/mef/filing/Originator.java` |
| IRS session | `mef-spring-boot-integration/src/main/java/com/irs/mef/filing/IrsSession.java` |
| Per-form facts (940, 941, 941-X) | `mef-spring-boot-integration/src/main/java/com/irs/mef/filing/FormSpec.java` |
| Schema release and returnVersion | `mef-spring-boot-integration/src/main/java/com/irs/mef/filing/SchemaRelease.java` |
| Journal states | `mef-spring-boot-integration/src/main/java/com/irs/mef/filing/JournalState.java` |
| Idempotency and replay | `FilingDesk.file` in `mef-spring-boot-integration/src/main/java/com/irs/mef/filing/FilingDesk.java` |
| Scenario list | `test-scenarios/*/scenario-*/scenario.json` |
| Paper copy: which MeF document prints on which IRS PDF | `mef-spring-boot-integration/src/main/java/com/irs/mef/returnpdf/IrsForm.java` |
| Web state to tone | `c2s-web/app/lib/states.js` |
| Web page list | `c2s-web/app/registry.js` |
| Web state contract | `c2s-web/generated/mef-facts.json` |

DocBlocksTest writes the three blocks below. Do not hand-edit them. `docs.update=true` rewrites them.

## Routes

<!-- generated:routes -->
GET /api/inspector/sends
GET /api/inspector/sends/{submissionId}
GET /api/inspector/sends/{submissionId}/pdf
GET /api/mef/filings/{submissionId}
GET /api/mef/session
GET /api/mef/session/certificate
POST /api/inspector/sends/{submissionId}/ack
POST /api/mef/filings
POST /api/mef/session/login
POST /api/mef/session/logout
POST /api/mef/wage-files
POST /api/mef/wage-files/{id}/receipt
<!-- /generated -->

## Environment variables

<!-- generated:env -->
MEF_ASID
MEF_CHECKBOOK_PUBLISHABLE_KEY
MEF_CHECKBOOK_SECRET_KEY
MEF_EFIN
MEF_ETIN
MEF_KEYSTORE_PASSWORD
MEF_KEYSTORE_PATH
MEF_KEY_ALIAS
MEF_RA_ADDRESS_LINE1
MEF_RA_CITY
MEF_RA_EIN
MEF_RA_FILING_IP
MEF_RA_NAME
MEF_RA_NAME_CONTROL
MEF_RA_PIN
MEF_RA_STATE
MEF_RA_ZIP
MEF_SOFTWARE_ID
MEF_SOFTWARE_ID_2027
MEF_SOFTWARE_ID_ANNUAL
<!-- /generated -->

## Scenarios

<!-- generated:scenarios -->
| id | form | title |
|---|---|---|
| 1 | 941 | Orchid Incorporated, baseline Form 941 |
| 2 | 941 | Marigold Corporation, semiweekly depositor with Schedule B |
| 3 | 941 | Daffodil Accounting, CPEO aggregate 941 with Schedule R and Form 8974 |
| 4 | 941X | Orchid Incorporated, Form 941-X correcting scenario 1 |
| 940-1 | 940 | Zinnia Company, single-state Form 940 with a refund |
| 940-2 | 940 | Poppy Company, multi-state Form 940 with Schedule A credit reduction |
| 940-3 | 940 | Crocus Company Inc, Section 3504 agent aggregate Form 940 with Schedule R and a balance due |
| 2027-1 | 941 | Orchid Incorporated, baseline TY2027 Form 941 |
| 2027-2 | 941 | Marigold Corporation, TY2027 semiweekly depositor with Schedule B |
| 2027-3 | 941 | Daffodil Accounting, TY2027 CPEO aggregate 941 with Schedule R and Form 8974 |
| 2027-4 | 941X | Orchid Incorporated, Form 941-X correcting TY2027 scenario 1 |
<!-- /generated -->

## Pyramos

Pyramos Software LLC files 94x returns as a Reporting Agent for other companies. The device certificate is in `Pyramos Software Certificates/` (`O=Pyramos Software LLC`, `CN=c2s.pahealthmanagement.org`, IGC Device CA 2, valid 2026-08-21 to 2027-08-21). Renew it before 2027-08-21.

Sends use EFIN 237861. EFIN 102192 starts with 10, and `Originator` refuses that prefix (R0000-118-01). ETINs: 44753 (Transmitter, production), 44754 (Software Developer, test; this one for ATS), 44762 (Online Provider). ASID 10219201 (the one behind every accepted send; the ASID only names the client app at login and is not tied to the EFIN). A spare ASID 23786102 ("Pyramos MeF A2A Client Annual", all ETINs, same cert) exists since 2026-11-03 and is unused. Software IDs: quarterly 26996214 (TY2026), annual 26888440 (TY2026, "Annual Form 2026"); the account also lists Onepay 26994140 (TY2026 quarterly) and Quarterly Forms 2027 27998441, neither used here. The values live in `mef-spring-boot-integration/.env` (gitignored).

ATS accepted the four quarterly scenarios under EFIN 237861: scenario 1 Orchid 941 `2378612026268yagqidh` (2026-09-25); scenario 2 Marigold 941 `2378612026287enq7gwn`, scenario 3 Daffodil 941 `237861202628741tq062`, scenario 4 Orchid 941-X `2378612026287svv97c5` (2026-10-14). Acks are in `mef-spring-boot-integration/data/ack-store/`. The TY2027 quarterly schema `2027Q1v2.0` is bundled (`FormSpec.schemaFor(taxYear)` picks the release; its Software ID is `MEF_SOFTWARE_ID_2027`) and ATS accepted scenario 2027-4 Orchid 941-X `2378612026307qe8dxko` on 2026-11-03 (ack `7GM9S3PXX2N9QDK800QA3SNWJP3FAB36TE1H2C2UCWMS8`, Software ID 27998441, quarter ending 2027-03 sent five months before the quarter closed, so ATS does not enforce R0000-191). ATS also accepted the three TY2027 Form 941 scenarios on 2026-11-03, each on the first ack poll with Software ID 27998441: 2027-1 Orchid `2378612026307fihd1kt` (ack `E4KF2LW38MDJSLGND16NRM6PGN87N052Q5HRPC09N62SQ`), 2027-2 Marigold `2378612026307ikiw5g4` (ack `CPQ7W4HV5KLX6M6RC85PPRG7KEWKTA45EC41BDB27BGAN`), 2027-3 Daffodil `2378612026307wp6t6cx` (ack `NPW3VR4516XVX4T8T66GEJUPE0MWSWBXL3TUVM5DHCE0G`), the last one on the April 2027 Schedule R layout. Every TY2027 94x quarterly ATS scenario is accepted. `./mef pdf` cannot yet print a 2027 return (`IrsForm` maps only 2026Q1v4.0 to `irs-forms/2026/`).

Form 940 validates against the IRS 2026v2.0 annual bundle in `mef-spring-boot-integration/src/main/resources/schemas/94x/2026v2.0/`, copied verbatim from the e-Services SOR mailbox drop in `94x-2026/`. The three 940 scenarios preflight clean, and each `line-mapping.tsv` cites the XSD `LineNumber` behind every element. ATS accepted all three 940 scenarios on 2026-11-03 with Software ID 26888440 and ASID 10219201, each ack on the first poll and without any e-Help call to open annual testing: 940-1 Zinnia `2378612026307ywjskwh` (ack `5Q1MJ19QMTNRC8WPMESQ5C0UC58AA5A9HM1LPSXTVJ9UU`), 940-2 Poppy `237861202630751tdicl` (ack `VPVKQSGMSKXCNTA7PXUEJV8JVVQTN7QKN04GAP9W6BC1S`), 940-3 Crocus `2378612026307z0dsve6` (ack `SJKBSVAGRCQAQK6TEC6X06228K94BVLGV4TL9QPS5MQ9Q`). A live 940 needs `MEF_SOFTWARE_ID_ANNUAL` (IRS issues a separate Software ID for the annual 94x package; `SoftwareIds` refuses a 940 without it). Whether an ATS accept on one 940 counts as the annual package pass for production is an e-Help question (866-255-0654).

Not yet built: per-client Form 8655 authorization. Any caller of `POST /api/mef/filings` can file any EIN under the Reporting Agent PIN, and the app has no authentication.

## Hard-won

- A submission id is the EFIN, the processing-date `yyyyDDD` (not the tax year), and 7 `[a-z0-9]`. `SubmissionIdTest` holds it.
- That same EFIN is in the id, the manifest, and OriginatorGrp. `ManifestXmlTest` holds it. The ETIN only authenticates. `IrsSession` holds the session.
- `IrsWire` builds the SDK submission from a filename and the XML string, and every `gov.irs` call there is reflective (`Class.forName`, then `invoke`).
- `FilingDesk` does not retry SendSubmissions. Identical client bytes replay the journaled receipt. The state machine is `JournalState`; the replay rule is on `FilingDesk.file`.
- The SOAP fault text is only in `a2a_sdk.log.*`. `IrsFault` carries that hint.
- An online-filer EFIN prefix, or a filing IP that is not a routable IPv4, is refused at startup. `OriginatorTest` holds it.
- IRS needs about 2 to 5 minutes after a send before an ack exists; an earlier GetAck finds nothing.
- Ack data quality: the EFIN may come back `999999` and TIN or amounts may be null. The EIN is the reliable identifier.
- Status and ack payloads arrive as MTOM ZIP attachments, not in the SOAP body; the SDK unpacks them.
- Never guess an SDK API: read `mef-spring-boot-integration/sdk-reference/docs/` first. `sdk-reference/extracted/` has no class files on a fresh clone; `mef-spring-boot-integration/sdk-reference/scripts/generate-sdk-docs.sh` regenerates them.
- `application.yml` names PRD as `www.irs.gov` while the SDK's `prd_endpoints.properties` uses `la.www4.irs.gov`. The SDK properties win; reconcile before production.

## Environment & Artifacts

- Save patches, SQL files, and reports to the repo root or `reports/`, never only to the session scratchpad. Always print the absolute path.
- Before writing a migration or phase script, confirm which rollout phases have actually run (DDL, shadow, reconcile, and so on) and whether the target tables hold data.
- Basecamp close-out: check for an existing todo before creating one, to avoid duplicates. If DNS or the API fails, output the todo text so it can be added manually.
- Before debugging an IRS reject or SDK error, search the repo for its code (`git grep R0000-118`, `X0000-008`) first. Past fixes live in `x0000-008-investigation-2026-09-16.html`, `test-scenarios/README.md` (Namespace Issues), and `.audit/`.

## Rules and what enforces them

When the operator corrects you, fix the mistake and add a row here. If the row already exists and nothing enforces it, that is a repeat: move it up a level (architecture, type, check) in the same change. Drop a row once its mistake cannot happen.

| Rule | Enforced by |
|---|---|
| A test that logs in to IRS runs only alone, armed by its own class name | `@LiveIrsTest` + `LiveIrsTestGuardTest` (fails if a test calls `.probe(` or `.login(` without the annotation, or on the retired `mef.integration.test.enabled`) |
| No OneWell identifiers (97661 / 238689 / 23868900) in new code or scripts | `RepoGuardTest.oneWellIdentifiersDoNotSpread` (per-file ratchet; line exceptions need `reason`, `expires`, `approved-by`) |
| Superseded docs stay deleted | `RepoGuardTest.retiredDocsStayDeleted` |
| Reporting Agent EFIN must not start 10/21/32/44/53 (R0000-118-01); filing IP must be a routable IPv4 (R0000-244) | `Originator` constructor + `OriginatorTest` |
| One EFIN in submission ID, manifest, and `OriginatorGrp` (R0000-054-01) | `SubmissionId.mint` and the composer both read `Originator`; `ManifestXmlTest` |
| Submission ID is `EFIN + yyyyDDD + 7 [a-z0-9]` | `SubmissionId`; `SubmissionIdTest` |
| Only `FilingDesk` reaches IRS; every SDK call holds the one session lock; a GET never reaches IRS | package-private `IrsWire`/`IrsSession.call` in `com.irs.mef.filing`; `IrsSessionConcurrencyTest`; `GetRoutesNeverReachIrsTest` |
| A form without a bundled schema cannot be sent | `FormSpec` takes a `SchemaBinding` (release plus XSD path) for every form; `XsdReturnValidator` refuses a path missing from the classpath; `AtsScenarioComposeTest` validates every scenario |
| The paper copy never drops a value we sent, and never prints the RA PIN | `ReturnXml.requireAllPrinted` fails the render; `Form941SheetTest` |
| No live send after a reject until each reject code has a local rule | `RejectLedger` inside `AtsRun`; `RejectLedgerTest`, `LocalRulesTest` |
| `./mef ats` exits 0 only on an Accepted ack | `AtsVerdict.exitCode`; `AtsVerdictTest` |
| The scenario directories are the only scenario list | `ScenarioDirectoryTest` |
| Docs name no deleted symbol or path, carry no raw build commands, and generated blocks match the code | `DocReferenceGuardTest`, `DocCommandGuardTest`, `DocBlocksTest` |
| The web state contract matches the Java states; one tone table; one page list | `ContractExportTest`, `c2s-web/app/lib/states.test.js`, `c2s-web/app/design-system/design-system.test.js`, `c2s-web/app/registry.test.js` |
| The offline suite stays green on every push and PR | `.github/workflows/test.yml` |
| Build and test on Java 17 only | `maven-enforcer-plugin` in `mef-spring-boot-integration/pom.xml`; `./mef check` doctor |
| Text files use LF; vendor drops and captures stay byte-exact | `.gitattributes` |
| `next build` must not break a running `next dev` | `c2s-web/next.config.js` picks `distDir` by phase |
| Never visit the TaxBandits website (taxbandits.com, any subdomain), by any tool or delegate; use IRS/SSA primary sources | `~/.claude/hooks/taxbandits-gate.sh` (PreToolUse deny on WebFetch, WebSearch, browser navigation, and shell fetches naming the domain) plus `permissions.deny` WebFetch rules in `~/.claude/settings.json`. A grok/Cursor brief is not gated: docs only |
| Personal identifiers and session credentials are masked in one place before anything reaches the browser; a sibling repo's script is loaded through one helper | `c2s-web/app/lib/redact.guard.test.js` fails on a second copy outside `c2s-web/app/lib/redact.js` or `c2s-web/app/lib/nodeRequire.js`; each catalog's boundary test asserts its raw SSNs are absent from the payload |
| Search the repo for a reject code before debugging it | Docs only (judgment) |
| Reports go to the repo root or `reports/`; migrations check rollout phases first; Basecamp todos are deduped | Docs only (judgment) |
