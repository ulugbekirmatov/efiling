// Client-safe. Ledger states come from ftb-fx/lib/store.js; not_sent and blocked are page states.
export const STATE_TONE = {
  not_sent: "waiting",
  blocked: "waiting",
  pending: "waiting",
  submitted: "waiting",
  superseded: "waiting",
  accepted: "safe",
  rejected: "attention",
  accepted_with_errors: "attention",
  needs_recovery: "attention",
};

export function stateTone(state) {
  return STATE_TONE[state] || "waiting";
}

export function stateLabel(state) {
  const text = String(state).replace(/_/g, " ");
  return text.charAt(0).toUpperCase() + text.slice(1);
}

export function formLabel(formKind) {
  return formKind === "B" ? "1094/1095-B" : "1094/1095-C";
}
