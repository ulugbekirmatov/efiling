const rowList = (v) => Object.values(v.rows ?? {});
const latest = (v) => {
  const rows = rowList(v);
  return rows.length ? { row: rows[rows.length - 1], index: rows.length } : null;
};
const someoneSaving = (v, index) =>
  Object.keys(v.pc).some((c) => v.pc[c] === "onwire" && v.mine[c] === index);

export const title = "Journal for one set of client bytes";

export const state = (v) => {
  if (!v.up && rowList(v).some((r) => r.state === "created")) return "Server down, a send open";
  const last = latest(v);
  if (!last) return "Not filed yet";
  const { row, index } = last;
  switch (row.state) {
    case "created":
      if (row.wire === "untouched") return "Submission id reserved, not sent";
      if (someoneSaving(v, index))
        return row.wire === "refused" ? "IRS refused it, saving" : "IRS has or may have it, saving";
      return row.wire === "refused"
        ? "Stuck open: IRS refused it, journal write failed"
        : "Stuck open: IRS has it, journal write failed";
    case "transmitted":
      return "Transmitted, receipt saved";
    case "rejected":
      return "IRS refused it, bytes free to resend";
    case "abandoned":
      return "Gave up before sending, bytes free to resend";
    case "indeterminate":
      return row.wire === "refused"
        ? "Marked unknown, though IRS refused it"
        : "Unknown whether IRS has it";
  }
  return "?";
};

export const group = (v) => {
  if (!v.up && rowList(v).some((r) => r.state === "created")) return "down";
  const last = latest(v);
  if (!last) return "free";
  const s = last.row.state;
  if (s === "rejected" || s === "abandoned") return "free";
  if (s === "created") return "open";
  return "settled";
};

export const colors = { free: "blue", open: "yellow", settled: "teal", down: "tan" };

export const step = (action, from, to) => {
  switch (action) {
    case "Reserve":
      return "a request reserves a submission id";
    case "GiveUpBeforeSend":
      return "the session lock, login, or composition fails";
    case "Send": {
      const changed = Object.keys(to.answer).find((c) => to.answer[c] !== from.answer[c]);
      const a = to.answer[changed];
      if (a === "ok") return "the IRS returns a receipt";
      if (a === "fault") return "the IRS returns a SOAP fault";
      return "the call ends with no clear answer";
    }
    case "SaveAnswer": {
      const changed = Object.keys(from.answer).find((c) => from.answer[c] !== to.answer[c]);
      const a = from.answer[changed];
      if (a === "ok") return "the journal saves the receipt";
      if (a === "fault") return "the journal saves the fault";
      return "the journal saves \"unknown\"";
    }
    case "SaveFails":
      return "the journal write fails";
    case "Crash":
      return "the server crashes";
    case "Restart":
      return "the server restarts";
    case "ReplaySaved":
      return "a repeat gets the saved receipt";
    case "ToldToReconcile":
    case "ReserveLosesToSaved":
      return "a repeat is told to reconcile first";
    case "LookupFree":
      return "a request finds the bytes unfiled";
    case "Done":
      return null;
  }
  return action;
};
