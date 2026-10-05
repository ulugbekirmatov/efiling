# c2s-web

Operator page that reads captured MeF sends from the Spring Boot backend: what we sent to IRS and the acknowledgment IRS returned.

## Design system

Build a new page with `app/design-system/`. `app/layout.js` imports `tokens.css` and `components.css` and renders `TopNav`, so the page imports no design-system CSS. Wrap the page in `page`. Put the title block in `page-head`.

Page shell: `page`, `page-head`, `eyebrow`, `intro`, `row`, `actions`, `actions end`, `muted`, `mono`, `crumbs`.

Surfaces: `card`, `card lift`, `card-title`, `callout`, `callout attention`, `callout safe`, `callout waiting`, `panel`, `panel-head`, `panel-section`, `panel-section tinted`, `empty`.

Data: `kv`, `table-wrap`, `data` (add `wide` at four columns or more), `nowrap`, `num`, `section-label`, `file-path`, `frame`. Render XML with `XmlCode` from `app/design-system/XmlCode.js`. Pass `tall` or `wrap` through its `className`.

Status: `Badge` and `Chip` from `app/design-system/Badge.js`. Map each domain state to a tone in `app/design-system/tones.js`, then pass that tone in. The tones are `attention` (needs a human: rejected, fault, risk), `safe` (accepted, confirmed, correct), `neutral` (a highlight with no judgment), and `waiting` (not sent yet, missing, deprioritized). `app/scenarios/ui.js` maps `accepted` to `safe` and `ready` to `waiting`. `toneClass` returns `tone-attention`, `tone-safe`, `tone-neutral`, or `tone-waiting`. An unknown tone becomes `tone-waiting`.

Controls: `btn btn-primary`, `btn btn-secondary`, `btn-mini`, `tabs` and `tab` (`aria-selected="true"` on the open tab), `select-list` and `select-item` (`aria-current="true"` on the open row).

A page that needs layout the shared classes do not cover adds one stylesheet beside `page.js` and imports it there (`send-inspector.css`, `scenarios.css`, `scenario.css`). That file sets layout: grid, flex, gap, margin. Color and font family stay `var(--token)` from `tokens.css`. Prefix every class with the page name (`inspector-layout`, `scenario-grid`). Imported CSS in the App Router is global and stays loaded after a client navigation, so an unprefixed name on one page restyles the other page.

`npm test` (from `c2s-web`) runs `app/design-system/design-system.test.js`. The test fails when a color literal sits outside `tokens.css`, and when a static `className` in `app/` matches no class in an `app/` stylesheet. Literals are `#` hex, `rgb`, `hsl`, `oklch`, and `color-mix`.

Muted text uses `--g600`. The `/html` skill uses `--g500` for muted text, and that pair is 3.5:1 on ivory. The primary button uses `--clay-d`. White on the skill's `--clay` is 3.1:1. Links are slate with a clay underline. Clay text on ivory is 3:1.

## Run both

Backend (from this folder):

```bash
cd ../mef-spring-boot-integration
mvn spring-boot:run
```

Frontend:

```bash
cd c2s-web
npm install
npm run dev
```

Open http://localhost:3000/send-inspector

`MEF_BACKEND_URL` (default `http://localhost:8080`) is the Spring Boot origin. Next.js reads it when rewrites are built (`next dev` start / `next build`), not at `next start` runtime. Rewrites map `/backend/:path*` to `${MEF_BACKEND_URL}/api/:path*`, so the browser stays same-origin.

**Fetch ack from IRS** is a live IRS GetAck call. Log in first with **Log in to IRS**. IRS typically needs 2–5 minutes after a send before an ack exists.

## Copy onto C2S later

These are the page files (the rest is Next.js shell):

- `app/send-inspector/page.js`
- `app/send-inspector/SendInspector.js`
- `app/send-inspector/api.js`
- `app/send-inspector/prettyXml.js`
- `next.config.js` (the `/backend` rewrite)

C2S QID/DID wiring is intentionally absent. Do not invent platform IDs here.

## ATS test scenarios

Open http://localhost:3000/scenarios

The pages read `test-scenarios/ats-ty2026/` on disk: `scenarios.json` and each scenario's XML, TSV, and PDF. The Spring Boot backend is not needed.

Set `MEF_REPO_ROOT` when `next dev` or `next build` does not run from `c2s-web`. The default repo root is the parent of `c2s-web`.

`composed-return.xml` and `manifest.xml` are regenerated with:

```bash
cd mef-spring-boot-integration
mvn test -Dtest=AtsScenarioSnapshotTest -Dats.snapshots.update=true
```

Scenario pages render per request, so regenerated snapshots show up on reload with no rebuild.

## AIR operator

The AIR operator pages are for the IRS AIR 1094-C/1095-C AATS session described in `TESTING.md` of the AIR package. They compose a transmission from an AATS fixture, validate and preview it, submit it to AATS, and check status. Each run is one directory under `AIR_RUNS_DIR`. The MeF Spring Boot backend is not needed.

| Variable | Default | Meaning |
| --- | --- | --- |
| `AIR_REPO_ROOT` | `$HOME/Documents/aca` | Root of the ACA checkout. The AIR package is `<AIR_REPO_ROOT>/redesign/air-a2a`. |
| `AIR_RUNS_DIR` | `$HOME/.air-operator/runs` | Directory for live run files. |
| `AIR_JAVA_HOME` | unset, then `JAVA_HOME`, then `/opt/homebrew/Cellar/openjdk/26.0.2.1/libexec/openjdk.jdk/Contents/Home` | JDK used to run the AIR jar. |
| `AIR_JAR` | `<AIR_REPO_ROOT>/redesign/air-a2a/java/target/air-a2a-channel-0.1.0-SNAPSHOT.jar` | Built AIR A2A channel jar. |
| `AIR_PKCS12` | unset | Path to the enrolled AATS PKCS12. |
| `AIR_P12_PASSWORD_ENV` | `AIR_P12_PASSWORD` | Name of the env var that holds the PKCS12 password, not the password itself. |
| `AIR_ASID` | unset | AIR System ID enrolled for AATS. |

Live runs are written under `AIR_RUNS_DIR`. The default sits outside both the c2s-web and AIR repos because those files hold SSN-shaped data.

**Build the AIR jar first.** The default `AIR_JAR` is `redesign/air-a2a/java/target/air-a2a-channel-0.1.0-SNAPSHOT.jar` under `AIR_REPO_ROOT`. A stale jar fails with `Invalid signature file digest for Manifest main attributes`. Readiness only checks that the jar file exists.

From `AIR_REPO_ROOT`, rebuild with the `mvn package` command in `redesign/air-a2a/java/README.md` and the `JAVA_HOME` it names.

```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk/26.0.2.1/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn -o -q -f redesign/air-a2a/java/pom.xml package
```

From `c2s-web`, start the app if it is not running.

```bash
npm run dev
```

Open http://localhost:3000/air/transmissions

The page lists live runs from `AIR_RUNS_DIR`. Pick a run to read the form, manifest, what we sent, and the IRS answer.

Open http://localhost:3000/air/scenarios

The page lists AATS fixtures from `<AIR_REPO_ROOT>/redesign/air-a2a/fixtures/aats`. Pick a scenario and compose a run. Compose writes one directory under `AIR_RUNS_DIR` and opens it on the transmissions page.

On a composed run, Validate (needs `xmllint` on PATH), then Preview, then Submit to AATS. Submit is AATS only (`testFileCd` must be `T`). Type the confirmation code. The server checks it. A run blocked by a local guard can submit again. After a submit that may have reached IRS, that UTID is spent. Compose a new run to get a new UTID.

Check status only while the stage is PROCESSING, a Receipt ID exists, and 10 minutes have passed since the last submit or status call that may have reached IRS. Live submit and status calls need AATS open (November 2026), the enrolled PKCS12, and the ASID. Preview uses the same PKCS12, ASID, and jar. It does not POST.

Every POST must be same-origin. The pages show SSN-masked form XML, redacted credentials, and redacted CLI records. Downloads of `form.xml` and `manifest.xml` are the exact bytes. The PKCS12 password is passed by env var name only.

**Check it offline.** From `c2s-web`, run `npm test`. The AIR tests cover the model, store, redaction, and views. Nothing in `npm test` contacts IRS.
