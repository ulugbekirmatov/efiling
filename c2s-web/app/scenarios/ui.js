export function formatEin(ein) {
  const digits = String(ein || "").replace(/\D/g, "");
  if (digits.length !== 9) return String(ein || "");
  return `${digits.slice(0, 2)}-${digits.slice(2)}`;
}

export const STATUS_STYLES = {
  accepted: { background: "#e6f6ec", color: "#1f7a4d" },
  ready: { background: "#fff8e8", color: "#9a6700" },
};

const BADGE = {
  display: "inline-block",
  fontSize: 11,
  fontWeight: 650,
  letterSpacing: "0.04em",
  borderRadius: 999,
  padding: "3px 8px",
  lineHeight: 1.4,
  maxWidth: "100%",
  wordBreak: "break-word",
};

export function statusLabel(status) {
  if (!status) return "";
  if (status.state === "ready") return "Ready, not sent";
  if (status.state === "accepted") {
    const parts = ["Accepted"];
    if (status.date) parts.push(status.date);
    if (status.submissionId) parts.push(status.submissionId);
    return parts.join(" · ");
  }
  return String(status.state);
}

export function StatusBadge({ status }) {
  const tone = STATUS_STYLES[status && status.state] || STATUS_STYLES.ready;
  return (
    <span style={{ ...BADGE, background: tone.background, color: tone.color }}>
      {statusLabel(status)}
    </span>
  );
}

export const SOURCE_STYLES = {
  client: { background: "#eef4f8", color: "#274869" },
  pyramos: { background: "#e6f6ec", color: "#1f7a4d" },
  generated: { background: "#eef1f5", color: "#5b677a" },
};

export function SourceBadge({ source }) {
  const tone = SOURCE_STYLES[source] || SOURCE_STYLES.generated;
  return (
    <span style={{ ...BADGE, background: tone.background, color: tone.color }}>{source}</span>
  );
}

export const CHIP = {
  display: "inline-block",
  fontSize: 11,
  fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
  background: "#eef4f8",
  color: "#274869",
  borderRadius: 999,
  padding: "2px 8px",
  lineHeight: 1.4,
};

export const MUTED_CHIP = {
  ...CHIP,
  background: "#eef1f5",
  color: "#5b677a",
};
