package com.irs.mef.inspector.view;

import com.irs.mef.inspector.ring.CapturedXml;
import com.irs.mef.inspector.ring.MimeRequest;
import com.irs.mef.inspector.ring.OmittedAttachment;
import com.irs.mef.inspector.ring.SendSnapshot;
import com.irs.mef.inspector.ring.SnapshotSummary;
import com.irs.mef.inspector.ring.SoapCapture;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * One panel. No templates engine. HTML-escape every document body.
 */
public final class InspectorHtml {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    private final int ringSize;

    public InspectorHtml() {
        this(20);
    }

    public InspectorHtml(int ringSize) {
        this.ringSize = Math.min(100, Math.max(1, ringSize));
    }

    /**
     * @param requested raw {@code ?id=} value when the operator asked for a specific send
     *                  (including invalid grammar). Empty means "show newest".
     */
    public String render(List<SnapshotSummary> list,
                         Optional<SendSnapshot> selected,
                         Optional<String> requested) {
        StringBuilder html = new StringBuilder(16_384);
        html.append("<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">");
        html.append("<title>SendSubmissions inspector</title>");
        html.append("<style>");
        html.append("body{font-family:ui-sans-serif,system-ui,sans-serif;margin:0;background:#f4f1ea;color:#1b1b1b;}");
        html.append("header.banner{background:#1f3b4d;color:#fff;padding:12px 20px;font-size:14px;}");
        html.append("header.banner strong{display:block;font-size:18px;margin-bottom:4px;}");
        html.append("main{display:grid;grid-template-columns:minmax(240px,320px) 1fr;min-height:100vh;}");
        html.append("ol.captures{list-style:none;margin:0;padding:12px;background:#e7e0d4;border-right:1px solid #cfc6b8;}");
        html.append("ol.captures li{margin:0 0 8px;}");
        html.append("ol.captures a{display:block;padding:10px;border-radius:8px;background:#fff;color:inherit;text-decoration:none;border:1px solid #d9d0c3;}");
        html.append("ol.captures li.current a{outline:2px solid #1f3b4d;}");
        html.append("ol.captures .id{font-family:ui-monospace,monospace;font-size:12px;}");
        html.append("article.selected{padding:16px 20px;}");
        html.append("article.selected h2{margin:0 0 8px;font-size:16px;}");
        html.append(".panes{display:grid;grid-template-columns:1fr 1fr;gap:16px;}");
        html.append("@media(max-width:960px){.panes,main{grid-template-columns:1fr;}}");
        html.append("section.return,section.mime{background:#fff;border:1px solid #d9d0c3;border-radius:8px;padding:12px;min-width:0;}");
        html.append("pre{overflow:auto;max-height:70vh;background:#0f1c24;color:#e8eef2;padding:12px;border-radius:6px;font-size:12px;white-space:pre-wrap;}");
        html.append("ol.mime-parts{font-family:ui-monospace,monospace;font-size:13px;padding-left:20px;}");
        html.append(".empty{padding:24px;font-size:16px;max-width:40em;}");
        html.append("details{margin-top:10px;}");
        html.append("</style></head><body>");
        html.append("<header class=\"banner\"><strong>SendSubmissions inspector</strong>");
        html.append(esc(banner()));
        html.append("</header><main>");
        html.append("<ol class=\"captures\">");
        if (list.isEmpty()) {
            html.append("<li>No captures in the last ").append(ringSize).append(" on this host.</li>");
        } else {
            String selectedId = selected.map(s -> s.submissionId().value()).orElse("");
            for (SnapshotSummary summary : list) {
                boolean current = summary.submissionId().value().equals(selectedId);
                html.append("<li");
                if (current) {
                    html.append(" class=\"current\"");
                }
                html.append("><a href=\"?id=").append(esc(summary.submissionId().value())).append("\">");
                html.append("<div>").append(esc(TIME.format(summary.capturedAt()))).append("</div>");
                html.append("<div class=\"id\">").append(esc(summary.submissionId().value())).append("</div>");
                html.append("<div>").append(esc(summary.einMasked())).append(" · ");
                html.append(esc(summary.formCode())).append(" · ");
                html.append(esc(summary.periodLabel())).append("</div>");
                html.append("<div>").append(summary.soapRequestCaptured() ? "SOAP captured" : "SOAP missing").append("</div>");
                html.append("</a></li>");
            }
        }
        html.append("</ol>");
        html.append("<article class=\"selected\">");
        if (selected.isEmpty() && requested.isEmpty() && list.isEmpty()) {
            html.append("<p class=\"empty\">");
            html.append(esc("Nothing captured on this host yet. File through NewSend, then refresh. This is the last "
                    + ringSize + " wire snapshots on this process, not an IRS archive."));
            html.append("</p>");
        } else if (selected.isEmpty() && requested.isPresent()) {
            html.append("<p class=\"empty\">");
            html.append(esc("Snapshot " + requested.get() + " is not in the last " + ringSize
                    + " on this host. It was evicted or never captured. This page does not query the IRS."));
            html.append("</p>");
        } else if (selected.isPresent()) {
            renderSelected(html, selected.get());
        }
        html.append("</article></main></body></html>");
        return html.toString();
    }

    private void renderSelected(StringBuilder html, SendSnapshot snapshot) {
        html.append("<p>Submission <span class=\"id\">")
                .append(esc(snapshot.submissionId().value()))
                .append("</span> · ")
                .append(esc(snapshot.einMasked()))
                .append(" · ")
                .append(esc(snapshot.formType().code()))
                .append(" · ")
                .append(esc(snapshot.taxPeriod().begin() + " to " + snapshot.taxPeriod().end()))
                .append(" · ")
                .append(esc(snapshot.environment()))
                .append("</p>");
        html.append("<div class=\"panes\">");
        html.append("<section class=\"return\"><h2>Return.xml</h2>");
        html.append("<p>sha256=").append(esc(snapshot.returnXmlSha256()));
        html.append(" (packed original; pretty-print is display-only)</p>");
        html.append("<pre>").append(esc(snapshot.returnXml().pretty())).append("</pre>");
        html.append("<details><summary>as packed</summary><pre>");
        html.append(esc(snapshot.returnXml().original()));
        html.append("</pre></details></section>");

        html.append("<section class=\"mime\"><h2>SendSubmissions MIME request</h2>");
        MimeRequest mime = snapshot.mimeRequest();
        html.append("<p>Content-Type: ").append(esc(mime.contentType().isBlank() ? "(not captured)" : mime.contentType()));
        html.append("</p>");
        html.append("<ol class=\"mime-parts\">");
        html.append("<li>part 0 SOAP ");
        Optional<CapturedXml> soap = mime.soapPart();
        if (soap.isPresent()) {
            html.append("text/xml — captured</li>");
        } else {
            String reason = mime.soapCapture() instanceof SoapCapture.Missing missing
                    ? missing.reason() : "SOAP part was not captured";
            html.append("— not captured: ").append(esc(reason)).append("</li>");
        }
        if (mime.attachments().isEmpty()) {
            html.append("<li>No MIME attachments inventoried.</li>");
        } else {
            for (OmittedAttachment attachment : mime.attachments()) {
                String type = attachment.contentType().isBlank() ? "application/octet-stream" : attachment.contentType();
                html.append("<li>").append(esc(shortType(type))).append(" omitted, ");
                html.append(attachment.byteLength()).append(" bytes, sha256=");
                html.append(esc(attachment.sha256()));
                if (!attachment.contentId().isBlank()) {
                    html.append(" cid=").append(esc(attachment.contentId()));
                }
                html.append("</li>");
            }
        }
        html.append("</ol>");
        if (soap.isPresent()) {
            html.append("<pre>").append(esc(soap.get().pretty())).append("</pre>");
        } else {
            html.append("<p>SOAP part was not captured — this pane does not invent an envelope.</p>");
        }
        html.append("<details class=\"response\"><summary>SOAP response</summary>");
        if (snapshot.soapResponse() instanceof SoapCapture.Present present) {
            html.append("<pre>").append(esc(present.xml().pretty())).append("</pre>");
        } else {
            String reason = snapshot.soapResponse() instanceof SoapCapture.Missing missing
                    ? missing.reason() : "no inbound SOAP";
            html.append("<p>").append(esc(reason)).append("</p>");
        }
        html.append("</details>");
        html.append("<details class=\"manifest\"><summary>manifest.xml as packed</summary>");
        html.append("<pre>").append(esc(snapshot.manifestXml().pretty())).append("</pre>");
        html.append("<details><summary>as packed</summary><pre>");
        html.append(esc(snapshot.manifestXml().original()));
        html.append("</pre></details></details>");
        html.append("</section></div>");
    }

    private String banner() {
        return "MIME SendSubmissions, not MTOM. Captured wire snapshot, not a reconstructed envelope. "
                + "SAML omitted. Attachment ZIP omitted. Last " + ringSize + " on this host.";
    }

    private static String shortType(String contentType) {
        int semi = contentType.indexOf(';');
        return semi < 0 ? contentType : contentType.substring(0, semi).trim();
    }

    static String esc(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
