# NewSend design record — SendSubmissions v2

Design record for the `com.irs.mef.newsend` vertical (architect workflow, 2026-08-31).
Four independent design candidates were produced in parallel, screened against design red flags,
and synthesized; this document is the surviving rationale. The legacy
`SubmissionController`/`SubmissionService` are untouched and keep their routes.

## Problem

We need a second, correct path to IRS MeF `SendSubmissions`, standing beside a legacy service we
may not touch and that has disqualifying defects: it reads the return XML from an arbitrary
caller-supplied server path on an unauthenticated endpoint; it reflects on
`gov.irs.mef.inputcomposition.SubmissionBinaryAttachment` — a class that does not exist in the
v16 jar, so the call cannot succeed at all; it hardcodes `FederalSubmissionTypeCd=941`; it reports
`accepted: true` for what is only a transmission receipt; and it keeps nothing after the HTTP
response returns.

The dominating constraint: **SendSubmissions is non-idempotent and un-retryable, and a duplicate
execution is a duplicate federal filing** — filed by Pyramos as a Reporting Agent on someone
else's tax account. Constraints carried in from grounding: reflection-only SDK access (Java 17
module rules vs the Metro stack); one non-thread-safe `ServiceContext` singleton owned by
`MefClientService` (a fresh one invalidates the IRS session); the legacy `GlobalExceptionHandler`
maps every `MefException` to 500 and must keep doing so for legacy routes; manifest element order
and EFIN/TIN formats are fixed by `efileAttachments.xsd`; the submission id is
EFIN(6) + yyyyDDD current-processing-date + 7 lowercase base36.

## Usage (caller's view)

```
POST /api/mef/newsend/submissions                          file one 94x return
GET  /api/mef/newsend/submissions/{submissionId}           read the local journal record
GET  /api/mef/newsend/submissions/by-request/{key}         reconcile by idempotency key
```

`Idempotency-Key` is a REQUIRED header on the POST. Same key + same payload replays the original
receipt with `replay: true` and transmits nothing. The caller supplies the return XML in the body
(never a path), the client EIN, the form type, and the tax period. The caller never supplies a
submission id (service-minted) or an EFIN (config, derived into the manifest from the id).

Success is `201` (or `200` on replay) with `state: TRANSMITTED`, the deposit id, and a receipt
timestamp — and a message stating this is receipt, not acceptance. The three failure shapes a
caller must handle: `409` (key or period conflict, `resubmitSafe: false`), `502`
(`NEWSEND_IRS_REJECTED` — IRS refused, nothing filed, re-file corrected under a new key), and
`504` (`NEWSEND_TRANSPORT_UNKNOWN`/`NEWSEND_NO_RECEIPT` — outcome unknown, do NOT resubmit,
reconcile via status/acks). Every error body carries `resubmitSafe`.

Batch call site: a quarterly job uses a deterministic key per (client, form, quarter), so a
crashed batch re-run re-files nothing; an `INDETERMINATE` result is parked for ack
reconciliation, never auto-retried.

## Shape

Package `com.irs.mef.newsend` — a self-contained vertical slice: `api/` (wire), `domain/` (pure),
`gateway/` (the only reflection), `journal/` (write-ahead log port + file/in-memory impls),
`validate/` (XXE-hardened well-formedness + optional 941 XSD), `error/` (sealed hierarchy, each
subtype owning its HTTP status). Happy path traces controller → service → gateway: three files.

- **The submission id is the spine.** `NewSendSubmissionId` derives its own `Efin`; the manifest
  reads EFIN from there, so "manifest EFIN == first 6 chars of the id" is true by construction
  (per encode-lessons-in-structure). One `Instant` produces both the id's yyyyDDD and the
  postmark (single source of truth).
- **Three wire outcomes, not two.** Sealed `NewSendOutcome` = `Transmitted | Rejected |
  Indeterminate`. The gateway never throws for a wire fault — a fault is a value — so "it threw,
  therefore nothing happened" is inexpressible. Only a recognised `ServiceException` (a SOAP
  fault: IRS received and refused) becomes `Rejected`; everything else is `Indeterminate`.
  A deposit id without a receipt matching OUR submission id is `Indeterminate`, not success.
- **Write-ahead journal** (per make-operations-idempotent): the CREATED row is fsync'd before the
  wire call; startup replay promotes lingering CREATED to INDETERMINATE, so a crash is never
  mistaken for a non-filing. `ABANDONED` is the only key-releasing state, reachable only from
  provably pre-wire failures (validation, lock timeout, composition, session lost).
- **Two duplicate defences:** the idempotency key catches machine retries (payload fingerprint
  distinguishes replay from key reuse); the `(EIN, form, period, environment)` guard catches the
  human re-run under a fresh key, overridable only by explicit `allowDuplicatePeriod: true`.
- **Session serialisation:** a fair `ReentrantLock` with bounded `tryLock` and an honest
  `503 + Retry-After` — per-actor sessions are impossible, so shared state is serialised
  explicitly (per separate-before-serializing-shared-state).
- **Boundary discipline:** wire DTOs convert to validated domain value types (`Ein`, `Efin`,
  `FormType`, `TaxPeriod`) in exactly one place; no `gov.irs.*` type and no reflection artifact
  crosses the gateway interface; validation errors carry field names.
- **Deliberately absent:** retry (the RetryTemplate bean exists and is consciously unwired),
  acks/status polling, ReturnHeader generation, Form 8655, a real database, auth, batching.

## Synthesis decision

Four parallel candidates (2× Opus, 2× Sonnet). **Base: candidate A** — deepest gateway contract
(outcomes as values; never-throws-for-wire-faults), the id-as-spine EFIN derivation, the
write-ahead journal with ABANDONED key release and crash promotion, and the JSONL file default.
Grafted from **B**: `resubmitSafe` on every error body, the payload fingerprint for
key-reuse detection, and 201-first/200-replay with `Location`. Grafted from **C/D**
(independently converged): `@RestControllerAdvice(assignableTypes=…)` + highest precedence so
legacy error behavior cannot change; D's single record shape feeding both the POST response and
the GET readback. Rejected: B's `onWireEntry` callback and persisted IN_FLIGHT state (stage
leakage; A's CREATED + startup promotion gives the same crash evidence with one fewer state,
biased safe), B's dry-run endpoint (additive later, not load-bearing), every async/202 variant
(all four candidates independently rejected it: SendSubmissions itself returns in seconds; async
deepens exactly the ambiguity this design removes), and D's separate command-factory bean (the
parse lives on the wire DTO — one fewer hop).

## Tradeoffs accepted

- We accept a mandatory `Idempotency-Key` header (400 without it) in exchange for making "retry
  the lost response" safe by default. Softening it to optional removes the protection exactly
  when a caller is least careful.
- We accept a 409 period guard that can block a legitimate correction, in exchange for catching
  the fresh-key human duplicate; the escape hatch is an explicit per-request boolean.
- We accept `INDETERMINATE` with no automatic resolution (a human or the ack flow closes it) in
  exchange for never guessing about a filing that may already be at the IRS.
- We accept JVM-wide serialisation of submits (503 under contention) in exchange for never
  putting two threads on the one `ServiceContext`. Quarterly filing volume makes throughput
  irrelevant; a corrupted IRS session is not.
- We accept a homegrown append-only JSONL journal in exchange for real crash durability now,
  behind a port a database can implement later without redesign.
- We accept ~30 small files in exchange for a pure, testable core and exactly one class that
  knows the SDK exists. The call chain — the thing that costs readers — is three files.
- We accept duplicating the 941 XSDs into `src/main/resources/schemas/94x/941/` in exchange for
  pre-flight schema validation instead of an opaque IRS reject minutes later.
- We accept boot-time WARN (not failure) on a missing/malformed EFIN so the app still starts for
  login/diagnostics without a `.env`; submits fail fast with `NEWSEND_CONFIGURATION`.

## Alternatives considered

- **Thin port of the legacy service with the bugs fixed.** Smaller diff, same-size surface hiding
  far less: the caller keeps the submission id, gets a 500 that discards it on a timeout, and has
  no duplicate protection. Lost on interface depth.
- **Returning the SDK's `SendSubmissionsResult` (or a mirror DTO).** Puts a transport type on the
  public surface and makes every caller re-decide whether a deposit id without a receipt is
  success. Rejected per boundary-discipline.
- **Async submit (202 + poll).** Attractive at Reporting Agent volume, but SendSubmissions
  returns in seconds — the 2–5 minute lag is on acknowledgements. Async buys latency relief we
  don't need at the cost of certainty we do, and doubles the states every client must model.
- **Idempotency derived from the natural filing identity only (no header).** Cannot distinguish
  a retry from a deliberate correction. Kept as the *secondary* guard instead.

## Open questions and risks

- Is `America/New_York` what ATS actually enforces for the yyyyDDD processing date near
  midnight? Confirm against an accepted ATS submission before PRD.
- Is `Rejected` keeping its idempotency key (new key required to re-file) too strict for
  operations, or the right audit posture?
- If ATS routinely returns sparse receipt lists, `NEWSEND_NO_RECEIPT → INDETERMINATE` will fire
  on healthy submissions — verify on the first ATS run and relax to deposit-id-plus-warning if so.
- The endpoints remain unauthenticated (no spring-security in the pom). The arbitrary-file-read
  hole is closed, but anyone who can reach :8080 can file under Pyramos's ETIN — put auth or
  network isolation in front before PRD.
- Single-instance assumption: the session lock and the journal are per-JVM. Two replicas against
  one ETIN are not protected. Write the single-instance commitment down, or move the journal to a
  shared store first.
- The session lock does not cover the legacy `SubmissionService`, which shares the same
  `ServiceContext`. Interleaving is possible if both paths are exercised concurrently; the fix
  (a gate inside `MefClientService`) means touching legacy — deferred deliberately.
- Which quarterly 941 schema version should XSD validation pin to, given the 2026 v1.0→v4.0
  churn? The bundled set is the one the offline tests already used.

## Next implementation step

Install the toolchain (`brew install openjdk@17 maven`, then the SDK jar per CLAUDE.md), run
`mvn test -Dtest='NewSend*Test'` (all offline), then exercise `POST /api/mef/newsend/submissions`
against ATS with the Scenario 1 return and confirm the receipt/deposit-id path end to end.
