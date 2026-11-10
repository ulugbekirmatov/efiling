import { Badge } from "../design-system/Badge";
import { toneFor } from "../lib/states.js";

export function formatEin(ein) {
  const digits = String(ein || "").replace(/\D/g, "");
  if (digits.length !== 9) return String(ein || "");
  return `${digits.slice(0, 2)}-${digits.slice(2)}`;
}

export function statusLabel(status) {
  if (!status) return "";
  if (status.state === "ready") return "Ready, not sent";
  if (status.state === "accepted") {
    const parts = ["Accepted"];
    if (status.date) parts.push(status.date);
    if (status.submissionId) parts.push(status.submissionId);
    return parts.join(" · ");
  }
  if (status.state === "partial") return "Partial";
  return String(status.state);
}

export function StatusBadge({ status }) {
  return <Badge tone={toneFor("mef.scenario", status && status.state)}>{statusLabel(status)}</Badge>;
}

export function SourceBadge({ source }) {
  return <Badge tone={toneFor("mef.headerSource", source)}>{source}</Badge>;
}
