import { Badge } from "../design-system/Badge";
import { TONES } from "../design-system/tones";

export function formatEin(ein) {
  const digits = String(ein || "").replace(/\D/g, "");
  if (digits.length !== 9) return String(ein || "");
  return `${digits.slice(0, 2)}-${digits.slice(2)}`;
}

export const STATUS_TONE = {
  accepted: TONES.safe,
  ready: TONES.waiting,
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
  return <Badge tone={STATUS_TONE[status && status.state]}>{statusLabel(status)}</Badge>;
}

export const SOURCE_TONE = {
  client: TONES.neutral,
  pyramos: TONES.safe,
  generated: TONES.waiting,
};

export function SourceBadge({ source }) {
  return <Badge tone={SOURCE_TONE[source]}>{source}</Badge>;
}
