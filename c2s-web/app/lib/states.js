import { TONES } from "../design-system/tones.js";

// The send inspector painted every one of these names neutral. Attention was only
// for a state string that contained "fault", and none of these do.
const JOURNAL = Object.freeze({
  CREATED: TONES.neutral,
  TRANSMITTED: TONES.neutral,
  REJECTED: TONES.neutral,
  INDETERMINATE: TONES.neutral,
  ABANDONED: TONES.neutral,
});

const ACK = Object.freeze({
  Accepted: TONES.safe,
  Rejected: TONES.attention,
});

const SCENARIO = Object.freeze({
  accepted: TONES.safe,
  ready: TONES.waiting,
  partial: TONES.attention,
});

const HEADER_SOURCE = Object.freeze({
  client: TONES.neutral,
  pyramos: TONES.safe,
  generated: TONES.waiting,
});

const AIR_STAGE = Object.freeze({
  composed: TONES.waiting,
  blocked: TONES.attention,
  sending: TONES.waiting,
  fault: TONES.attention,
  PROCESSING: TONES.waiting,
  ACCEPTED: TONES.safe,
  ACCEPTED_WITH_ERRORS: TONES.attention,
  PARTIALLY_ACCEPTED: TONES.attention,
  REJECTED: TONES.attention,
  NOT_FOUND: TONES.attention,
});

const FTB_STATE = Object.freeze({
  not_sent: TONES.waiting,
  blocked: TONES.waiting,
  pending: TONES.waiting,
  submitted: TONES.waiting,
  superseded: TONES.waiting,
  accepted: TONES.safe,
  rejected: TONES.attention,
  accepted_with_errors: TONES.attention,
  needs_recovery: TONES.attention,
});

export const DOMAIN_TONES = Object.freeze({
  "mef.journal": JOURNAL,
  "mef.ack": ACK,
  "mef.scenario": SCENARIO,
  "mef.headerSource": HEADER_SOURCE,
  "air.stage": AIR_STAGE,
  "ftb.state": FTB_STATE,
});

export function toneFor(domain, state) {
  const table = DOMAIN_TONES[domain];
  if (table && Object.prototype.hasOwnProperty.call(table, state)) return table[state];
  return TONES.waiting;
}
