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
