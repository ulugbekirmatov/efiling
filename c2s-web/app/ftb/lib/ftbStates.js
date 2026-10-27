// Client-safe. Ledger states come from ftb-fx/lib/store.js; not_sent and blocked are page states.

export function stateLabel(state) {
  const text = String(state).replace(/_/g, " ");
  return text.charAt(0).toUpperCase() + text.slice(1);
}

export function formLabel(formKind) {
  return formKind === "B" ? "1094/1095-B" : "1094/1095-C";
}
