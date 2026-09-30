import Link from "next/link";
import { notFound } from "next/navigation";
import { prettyXml } from "../../../send-inspector/prettyXml";
import { loadLegacy } from "../../scenarioData";

export const dynamic = "force-dynamic";

const styles = {
  page: {
    maxWidth: 1280,
    margin: "0 auto",
    padding: "28px 20px 64px",
  },
  crumb: { margin: "0 0 14px", fontSize: 13 },
  header: {
    margin: "0 0 10px",
    fontSize: 28,
    fontWeight: 600,
    letterSpacing: "-0.02em",
  },
  description: { margin: "0 0 10px", fontSize: 15, maxWidth: 760 },
  path: {
    margin: "0 0 16px",
    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
    fontSize: 12,
    color: "#5b677a",
    wordBreak: "break-word",
  },
  missing: {
    background: "#fff8e8",
    border: "1px solid #f0d9a0",
    color: "#9a6700",
    borderRadius: 8,
    padding: "10px 12px",
    fontSize: 13,
  },
  pre: {
    margin: 0,
    padding: 12,
    background: "#0f1724",
    color: "#e8eef7",
    borderRadius: 8,
    fontSize: 12,
    lineHeight: 1.45,
    overflow: "auto",
    maxHeight: "70vh",
    whiteSpace: "pre",
  },
  iframe: {
    width: "100%",
    height: "80vh",
    border: "1px solid #d8dee8",
    borderRadius: 8,
    background: "#fff",
  },
  actions: { display: "flex", flexWrap: "wrap", gap: 8, marginBottom: 12 },
  toggle: {
    border: "1px solid #d8dee8",
    background: "#fff",
    borderRadius: 6,
    padding: "4px 10px",
    font: "inherit",
    fontSize: 13,
    color: "inherit",
    textDecoration: "none",
    display: "inline-block",
  },
};

export default function LegacyItemPage({ params }) {
  const loaded = loadLegacy(params.id);
  if (!loaded) notFound();
  const { item, content, hasFile } = loaded;
  const fileHref = `/scenarios/legacy/${item.id}/file`;

  let body = null;
  if (!hasFile) {
    body = <p style={styles.missing}>Not prepared for this scenario</p>;
  } else if (item.kind === "pdf") {
    body = (
      <>
        <div style={styles.actions}>
          <a href={fileHref} target="_blank" rel="noreferrer" style={styles.toggle}>
            Open in new tab
          </a>
        </div>
        <iframe title={item.title} src={fileHref} style={styles.iframe} />
      </>
    );
  } else if (item.kind === "xml") {
    body = <pre style={styles.pre}>{prettyXml(content)}</pre>;
  } else {
    body = <pre style={styles.pre}>{content}</pre>;
  }

  return (
    <div style={styles.page}>
      <p style={styles.crumb}>
        <Link href="/scenarios">ATS test scenarios</Link>
        {" / "}
        {item.title}
      </p>
      <h1 style={styles.header}>{item.title}</h1>
      <p style={styles.description}>{item.description}</p>
      <p style={styles.path}>{item.path}</p>
      {body}
    </div>
  );
}
