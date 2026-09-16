# c2s-web

Small Next.js app with one operator page: pick a sample send and read Return XML plus SendSubmissions MIME/SOAP. Fixtures only — no IRS, no C2S query IDs, no Nashorn backends.

```bash
cd c2s-web
npm install
npm run dev
```

Open http://localhost:3000/send-inspector

## Copy onto C2S later

These are the page files (the rest is Next.js shell):

- `app/send-inspector/page.js`
- `app/send-inspector/SendInspector.js`
- `app/send-inspector/fixtures.js`
- `app/send-inspector/prettyXml.js`

C2S QID/DID wiring is intentionally absent. Do not invent platform IDs here.
