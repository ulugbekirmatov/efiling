# c2s-web

The design system in `app/design-system/` is the default and the only styling system for this Next.js 14 app. `README.md` lists the classes and the page-building steps.

`app/layout.js` imports `tokens.css` and `components.css`. A page imports no design-system CSS. No Tailwind, no CSS-in-JS, no `style` objects. Color literals stay in `tokens.css`.

Reuse a `components.css` class before adding page CSS. Page CSS is one file per page, layout only, `var(--token)` for color, class names prefixed with the page name. A class a second page needs goes in `components.css`, and every consumer moves to it.

Map domain states through `tones.js` (`attention`, `safe`, `neutral`, `waiting`) and render them with `Badge` or `Chip`. Render XML with `XmlCode`.

Write app code as ES modules in plain `.js` files. Do not add TypeScript.

Leave the contrast choices as they are: muted text on `--g600`, the primary button on `--clay-d`, links in slate with a clay underline. `README.md` records the ratios.

From this directory, finish with `npm test` and `npx next build`. Never run `next build` while `next dev` is running. They share `.next`.

AIR pages live under `app/air/`. The pure state model is `app/air/lib/runModel.js`. Every stage, tone, and next action comes from `STAGES` there. Client modules may import `runModel.js`. Import `airConfig.js`, `runStore.js`, `airTools.js`, `scenarioCatalog.js`, `redact.js`, and `http.js` only from route handlers and server pages. Readiness reports `jar` when the file exists and does not verify it. `npm test` covers the model, store, redaction, and views. Nothing in `npm test` contacts IRS. Never run a live submit or status from a test.
