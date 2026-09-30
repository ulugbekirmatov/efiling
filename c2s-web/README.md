# c2s-web

Operator page that reads captured MeF sends from the Spring Boot backend: what we sent to IRS and the acknowledgment IRS returned.

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
