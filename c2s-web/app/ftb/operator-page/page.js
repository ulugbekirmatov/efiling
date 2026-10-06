import { Badge, Chip } from "../../design-system/Badge";
import "./ftb-operator-page.css";

// PR writeup for aca commit cc8897d (ftb-fx operator page). Static content, no data source.

const SUMMARY_BADGES = [
  { tone: "safe", text: "tests 261 / 261" },
  { tone: "safe", text: "scenarios passing 5 / 5" },
];
const SUMMARY_CHIPS = ["files 9", "lines +1107 −34", "pushed to main"];

const BEFORE = [
  "Scenario data lived in JSON fixtures with no view.",
  "FTB's narrative lived in a PDF and a text copy.",
  "Checks ran only inside the CLI or the test suite.",
  "The next step for a ledger state meant reading the TESTING.md table.",
];
const AFTER = [
  "One page, one tab per scenario, in testing order 1, 2, 2C, 5, 5C.",
  "FTB's text quoted with its spec line numbers.",
  "Our data as tables, with a January to December coverage grid.",
  "Five checks per scenario, including a match against FTB's answer key.",
  <>
    The exact next <code>fx.js</code> command for each scenario's state.
  </>,
];

const OPTIONS = [
  {
    option: (
      <>
        Static generator writing <code>.cache/operator.html</code>
      </>
    ),
    gains: "Nothing can send. No process to keep running. Rerun to refresh.",
    costs: "Status is as fresh as the last run.",
    chosen: true,
  },
  {
    option: (
      <>
        Local <code>node:http</code> server with buttons
      </>
    ),
    gains: "Always current status. Compose on demand.",
    costs: "A live send sits one click away. More code to own.",
    chosen: false,
  },
];

const STORIES = [
  {
    title: "1 · The page sent you to a command that skips the correction",
    body: (
      <p>
        Scenario 2C corrects scenario 2. Suppose FTB answers scenario 2 with "Accepted with Errors". The first
        version treated that as good enough and told you to run <code>cycle --live</code>. But <code>cycle</code>{" "}
        stops at that state, so 2C would never go out, and nothing would tell you why.
      </p>
    ),
    example: {
      head: "Worked example · ledger after the first live send",
      rows: [
        { label: "Scenario 2 state", value: "accepted_with_errors" },
        { label: "Scenario 2C row", value: "none" },
        { label: "Old page said", value: "Not sent yet. Run cycle --live", tone: "attention" },
        { label: "What cycle does", value: "stops at 2, never reaches 2C", tone: "attention" },
        { label: "New page says", value: "Blocked. Fix 2 by hand (Part 2 §10.2 to 10.4)", tone: "safe" },
      ],
    },
    paths: ["ftb-fx/scripts/operator-page.js:107", "ftb-fx/scripts/operator-page.js:131"],
    code: [
      { kind: "del", text: "var CORRECTABLE = { accepted: true, accepted_with_errors: true };" },
      { text: "function acceptedOriginalRow(rows, id) {" },
      {
        kind: "add",
        text: "    return lastWhere(rows, function (r) { return r.scenario === id && r.state === 'accepted' && Boolean(r.receiptId); });",
      },
      { text: "}" },
      { kind: "add", text: "                if (original && original.state === 'accepted_with_errors') {" },
      {
        kind: "add",
        text: "                    return 'Blocked. Scenario ' + scenario.correctionOf + ' is Accepted with Errors and cycle stops there. ...';",
      },
    ],
  },
  {
    title: "2 · Sharing one compare quietly loosened the 1094/1095-C test",
    body: (
      <p>
        The B and C compose tests each had their own way to compare our XML with FTB's answer key. The page needed
        the same compare, so the first version merged them into one. The merged version ignored two attributes for
        both forms. The B test had always ignored them, but the C test had always checked them. A wrong{" "}
        <code>documentId</code> on a 1095-C would have passed.
      </p>
    ),
    example: {
      head: "Worked example · a 1095-C with the wrong documentId",
      rows: [
        { label: "Answer key", value: 'documentId="1095C-1"' },
        { label: "Our output (bug)", value: 'documentId="1095C-9"' },
        { label: "Merged compare", value: 'both become "D" → pass', tone: "attention" },
        { label: "Per-form compare", value: "C keeps documentId → fail", tone: "safe" },
      ],
    },
    paths: ["ftb-fx/lib/answer-key.js:33", "ftb-fx/lib/answer-key.js:46"],
    code: [
      { kind: "comment", text: "// Only the fields that legitimately differ from FTB's 2023 answer keys." },
      { text: "var NORMALIZERS = {" },
      { text: "    B: [TIMESTAMP, TAX_YEAR, DOCUMENT_ID, SCHEMA_LOCATION]," },
      { text: "    C: [TIMESTAMP, TAX_YEAR]" },
      { text: "};" },
      { kind: "del", text: "function normalize(xmlText) {" },
      { kind: "add", text: "function normalize(xmlText, formKind) {" },
      { kind: "add", text: "    var steps = NORMALIZERS[formKind];" },
      { kind: "add", text: "    if (!steps) throw new Error('unknown form kind: ' + formKind);" },
    ],
  },
  {
    title: "3 · Some tests would pass even if the compare returned nothing",
    body: (
      <p>
        The answer-key tests ran both sides through the same compare and checked that the results were equal. If
        the compare broke and returned <code>undefined</code>, both sides would be <code>undefined</code>, and the
        test would still pass. The new tests check the compare against fixed expected strings, and include a pair
        that must fail.
      </p>
    ),
    example: {
      head: "Worked example · normalize breaks and returns undefined",
      rows: [
        { label: "normalize(ours)", value: "undefined" },
        { label: "normalize(key)", value: "undefined" },
        { label: "Old test", value: "undefined === undefined → pass", tone: "attention" },
        { label: "New test", value: "expects a literal string → fail", tone: "safe" },
      ],
    },
    paths: [
      "ftb-fx/tests/answer_key_test.js",
      "ftb-fx/tests/operator_page_test.js",
      "ftb-fx/scripts/operator-page.js:334",
    ],
    code: [
      { kind: "comment", text: "// answerKeyCheck on a mismatching pair reports the first differing line" },
      { text: "assert.equal(result.ok, false);" },
      { text: "assert.deepEqual(result.details, ['line 3 ours: <C>two</C>   key: <C>changed</C>']);" },
    ],
  },
];

const CODE_LINE_CLASS = { add: "ftb-op-add", del: "ftb-op-del", comment: "cm" };

const FILES = [
  {
    path: "ftb-fx/scripts/operator-page.js",
    stat: "+787",
    open: true,
    why: (
      <>
        The generator. One <code>SCENARIOS</code> table (line 31) holds each scenario's form type, record IDs, which
        original a correction waits on, its narrative lines in the spec, and its audit rule numbers.{" "}
        <code>buildModel</code> composes each scenario in dry run (<code>dryRun: true</code>, line 379) into{" "}
        <code>.cache/preview/</code>. It reads the ledger with plain <code>fs</code> and never calls{" "}
        <code>openLedger</code>, because that takes a lock and creates files (line 85). <code>renderPage</code> turns
        the model into one self-contained HTML file in your <code>html</code> skill's Anthropic style. Every value
        goes through <code>escapeHtml</code>.
      </>
    ),
  },
  {
    path: "ftb-fx/lib/answer-key.js",
    stat: "+63",
    why: (
      <>
        The one answer-key compare, shared by the page and both compose tests. <code>normalize(xml, formKind)</code>{" "}
        reads a per-form table of fields to ignore. <code>keyReceiptId</code> pulls the sample ReceiptId from the 2C
        and 5C keys so a correction can be previewed before the original is Accepted.
      </>
    ),
  },
  {
    path: "ftb-fx/tests/operator_page_test.js",
    stat: "+192",
    why: (
      <>
        The page's behavior. Five scenarios in order. Every answer-key check passes. 2C and 5C stay blocked with no
        ledger and with an "Accepted with Errors" original. An Accepted scenario 2 row puts its real ReceiptId into
        the 2C preview. The HTML has no external URLs and escapes an injected <code>&lt;script&gt;</code>. Tests use
        a fake CA-TCC and a temp ledger path, so they never touch real state.
      </>
    ),
  },
  {
    path: "ftb-fx/tests/answer_key_test.js",
    stat: "+40",
    why: "Checks the compare against literal strings for B and for C, the unknown-form-kind error, and the shape of the key ReceiptIds. This is the fix for story 3.",
  },
  {
    path: "ftb-fx/tests/compose_b_test.js, compose_c_test.js",
    stat: "+ / −34",
    why: (
      <>
        Their local compare copies are deleted, and both import <code>lib/answer-key.js</code>. Their assertions are
        unchanged. The C test compares <code>documentId</code> and <code>schemaLocation</code> exactly, as it did
        before.
      </>
    ),
  },
  {
    path: "ftb-fx/README.md, TESTING.md, DESIGN.md",
    stat: "+16",
    why: 'README gets an "Operator page" section with the command. TESTING.md tells the operator to open the page before "Compose and dry run". DESIGN.md lists the two new modules and their exports.',
  },
];

const REVIEW_FOCUS = [
  {
    title: "The page must never send or write the ledger",
    detail: (
      <>
        Compose runs with <code>dryRun: true</code> and a fixed UUID. The ledger is read with{" "}
        <code>fs.readFileSync</code> only. A corrupt ledger shows a note instead of crashing.
      </>
    ),
  },
  {
    title: "The next-step text must match TESTING.md",
    detail: (
      <>
        <code>nextAction</code> maps every ledger state to the runbook's wording. A wrong mapping here sends the
        operator down the wrong path, as story 1 showed.
      </>
    ),
  },
  {
    title: "Narrative line ranges",
    detail:
      "The ranges start at each scenario heading in the Testing Specifications text (lines 703, 805, 870, 918, 1031). If FTB publishes a new spec, these need a recheck.",
  },
];

const VERIFICATION = [
  {
    done: true,
    text: (
      <>
        <code>node --test 'ftb-fx/tests/*_test.js'</code>: 261 pass, 0 fail. It was 243 before this work.
      </>
    ),
  },
  {
    done: true,
    text: "The generated page shows 5 of 5 checks passing on every scenario. 2C and 5C show as blocked, since no ledger exists yet.",
  },
  { done: true, text: "A script searched the page for the app key, secret key and app ID values. None were found." },
  {
    done: true,
    text: "Chrome via DevTools: tabs show one panel at a time, collapsible sections toggle, no console errors, no external requests, no sideways scroll at phone width, dark mode renders.",
  },
  {
    done: true,
    text: "Grok 4.6 ran a read-only review across six risk areas and found three real bugs, all fixed (stories 1 to 3).",
  },
  {
    done: false,
    text: "A human read of the page against the FTB PDF. The test SSNs keep the page out of my context, so this is yours.",
  },
];

const ROLLOUT = [
  { when: "Now", text: "Open the page. Read each scenario's narrative against our tables." },
  {
    when: "Before live",
    text: (
      <>
        Run <code>fx.js cycle</code> as a dry run.
      </>
    ),
  },
  {
    when: "Live",
    text: (
      <>
        Send 1, 2 and 5 with <code>--live</code>. Rerun the page to see states.
      </>
    ),
  },
  { when: "After Accepted", text: "Send 2C and 5C once the page unblocks them." },
  { when: "Last", text: "Submit all five ReceiptIds on the FX Portal for evaluation." },
];

const OPEN_DECISIONS = [
  {
    title: "Share it or keep it local",
    detail:
      "The page holds the CA-TCC and FTB's fake test SSNs, so it was not published. A copy with masked SSNs could be shared if a teammate needs it.",
  },
  {
    title: "Live status later",
    detail:
      "Once testing starts, a refresh after each status poll may get tedious. A small watch mode that regenerates on ledger change would fix that without adding a send button.",
  },
];

export const metadata = { title: "FTB operator page PR · C2S operator" };

function Section({ eyebrow, title, note, children }) {
  return (
    <section className="ftb-op-section">
      <p className="eyebrow">{eyebrow}</p>
      <h2>{title}</h2>
      {note && <p className="ftb-op-note">{note}</p>}
      {children}
    </section>
  );
}

function NumberedCards({ items }) {
  return (
    <ol className="ftb-op-steps">
      {items.map((item, index) => (
        <li key={item.title} className="card ftb-op-step">
          <span className="ftb-op-step-n">{index + 1}</span>
          <div>
            <p className="ftb-op-step-title">{item.title}</p>
            <p className="muted">{item.detail}</p>
          </div>
        </li>
      ))}
    </ol>
  );
}

function CodeBlock({ lines }) {
  return (
    <div className="code">
      <pre>
        {lines.map((line, index) => (
          <span key={index}>
            <span className={CODE_LINE_CLASS[line.kind]}>{line.text}</span>
            {"\n"}
          </span>
        ))}
      </pre>
    </div>
  );
}

function Story({ story }) {
  return (
    <article className="card ftb-op-story">
      <h3>{story.title}</h3>
      {story.body}
      <div className="panel ftb-op-example">
        <div className="panel-head">{story.example.head}</div>
        <dl className="kv panel-section mono">
          {story.example.rows.map((row) => [
            <dt key={`${row.label}-dt`}>{row.label}</dt>,
            <dd key={`${row.label}-dd`}>{row.tone ? <Badge tone={row.tone}>{row.value}</Badge> : row.value}</dd>,
          ])}
        </dl>
      </div>
      <div className="row ftb-op-paths">
        {story.paths.map((path) => (
          <Chip key={path}>{path}</Chip>
        ))}
      </div>
      <CodeBlock lines={story.code} />
    </article>
  );
}

export default function FtbOperatorPagePrPage() {
  return (
    <div className="page">
      <header className="page-head">
        <p className="eyebrow">PR writeup · aca · ftb-fx · commit cc8897d</p>
        <h1>A page to learn the FTB test scenarios</h1>
        <p className="intro">
          Before we send anything to California's File Exchange, the operator needs to know what each of the five
          test scenarios asks for. This commit adds a read-only page that shows exactly that, next to what we would
          send.
        </p>
        <div className="row ftb-op-badges">
          {SUMMARY_BADGES.map((badge) => (
            <Badge key={badge.text} tone={badge.tone}>
              {badge.text}
            </Badge>
          ))}
          {SUMMARY_CHIPS.map((text) => (
            <Chip key={text}>{text}</Chip>
          ))}
        </div>
      </header>

      <div className="callout ftb-op-tldr">
        <p className="section-label">TL;DR</p>
        <p>
          Run <code>node --env-file-if-exists=ftb-fx/.env ftb-fx/scripts/operator-page.js --open</code> and you get
          one page per scenario. Each shows FTB's own words, our data, the local checks, and the next command to run.
        </p>
        <p>
          The page cannot send, cannot write the ledger, and never shows a secret key. A second model found three
          real bugs before the commit, and all three are fixed.
        </p>
      </div>

      <Section
        eyebrow="Motivation"
        title="Why we needed this"
        note="FTB accepts only its five predefined scenarios, and the data must match their narrative exactly. Until now the only way to see a scenario was to read a JSON fixture, a PDF, and a 276-line audit side by side. One mistake in a live send burns a single-use transmission ID and stays on FTB's record."
      >
        <div className="ftb-op-two-col">
          <div className="card">
            <p className="section-label">Before</p>
            <ul className="ftb-op-list">
              {BEFORE.map((text) => (
                <li key={text}>{text}</li>
              ))}
            </ul>
          </div>
          <div className="card ftb-op-after">
            <p className="section-label">After</p>
            <ul className="ftb-op-list">
              {AFTER.map((text, index) => (
                <li key={index}>{text}</li>
              ))}
            </ul>
          </div>
        </div>
      </Section>

      <Section
        eyebrow="Design choice"
        title="A generated file, not a running server"
        note="A server would show live status and could compose on a button click. It would also put a send button one click from an irreversible filing. The page is for learning before testing starts, so safety beat freshness."
      >
        <div className="table-wrap">
          <table className="data wide">
            <thead>
              <tr>
                <th>Option</th>
                <th>Gains</th>
                <th>Costs</th>
                <th className="nowrap">Pick</th>
              </tr>
            </thead>
            <tbody>
              {OPTIONS.map((row) => (
                <tr key={row.gains}>
                  <td>{row.option}</td>
                  <td>{row.gains}</td>
                  <td>{row.costs}</td>
                  <td className="nowrap">
                    {row.chosen ? <Badge tone="safe">Chosen</Badge> : <Badge tone="waiting">Not chosen</Badge>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Section>

      <Section
        eyebrow="What the review caught"
        title="Three bugs fixed before the commit"
        note="Grok reviewed the first version read-only. Each finding below was real, and each card shows the case it would have failed."
      >
        {STORIES.map((story) => (
          <Story key={story.title} story={story} />
        ))}
      </Section>

      <Section eyebrow="File tour" title="What changed, file by file">
        <div className="ftb-op-files">
          {FILES.map((file) => (
            <details key={file.path} className="panel ftb-op-file" open={file.open}>
              <summary className="ftb-op-file-summary">
                <span className="mono">{file.path}</span>
                <span className="mono muted">{file.stat}</span>
              </summary>
              <div className="panel-section">
                <p className="ftb-op-note">{file.why}</p>
              </div>
            </details>
          ))}
        </div>
      </Section>

      <Section eyebrow="Review focus" title="Where to look hardest">
        <NumberedCards items={REVIEW_FOCUS} />
      </Section>

      <Section eyebrow="Verification" title="How it was proven">
        <div className="ftb-op-checks">
          {VERIFICATION.map((check, index) => (
            <div key={index} className="card ftb-op-check">
              {check.done ? <Badge tone="safe">Done</Badge> : <Badge tone="waiting">Open</Badge>}
              <div>{check.text}</div>
            </div>
          ))}
        </div>
      </Section>

      <Section eyebrow="How to use it" title="From learning to Production CA-TCC">
        <ol className="ftb-op-rollout">
          {ROLLOUT.map((step, index) => (
            <li key={step.when} className="card">
              <p className="section-label">{step.when}</p>
              <p className="ftb-op-rollout-n">{index + 1}</p>
              <p className="muted">{step.text}</p>
            </li>
          ))}
        </ol>
      </Section>

      <Section eyebrow="Open decisions" title="Left for you">
        <NumberedCards items={OPEN_DECISIONS} />
      </Section>
    </div>
  );
}
