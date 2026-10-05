import Link from "next/link";
import { notFound } from "next/navigation";
import { prettyXml } from "../../../send-inspector/prettyXml";
import XmlCode from "../../../design-system/XmlCode";
import { loadLegacy } from "../../scenarioData";

export const dynamic = "force-dynamic";

export default function LegacyItemPage({ params }) {
  const loaded = loadLegacy(params.id);
  if (!loaded) notFound();
  const { item, content, hasFile } = loaded;
  const fileHref = `/scenarios/legacy/${item.id}/file`;

  let body = null;
  if (!hasFile) {
    body = <p className="callout waiting">Not prepared for this scenario</p>;
  } else if (item.kind === "pdf") {
    body = (
      <>
        <div className="actions">
          <a href={fileHref} target="_blank" rel="noreferrer" className="btn-mini">
            Open in new tab
          </a>
        </div>
        <iframe title={item.title} src={fileHref} className="frame" />
      </>
    );
  } else if (item.kind === "xml") {
    body = <XmlCode text={prettyXml(content)} className="tall" label={item.title} />;
  } else {
    body = (
      <div className="code tall">
        <pre>{content}</pre>
      </div>
    );
  }

  return (
    <div className="page">
      <p className="crumbs">
        <Link href="/scenarios">ATS test scenarios</Link>
        {" / "}
        {item.title}
      </p>
      <header className="page-head">
        <p className="eyebrow">{item.kind}</p>
        <h1>{item.title}</h1>
        <p className="intro">{item.description}</p>
        <p className="file-path">{item.path}</p>
      </header>
      {body}
    </div>
  );
}
