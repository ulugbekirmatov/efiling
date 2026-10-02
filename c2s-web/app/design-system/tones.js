// The /html skill's color-semantic code. Domain modules map their own states onto these names;
// components.css owns the colors behind each `tone-*` class.
export const TONES = Object.freeze({
  attention: "attention", // needs a human: rejected, fault, risk
  safe: "safe", // accepted, confirmed, correct
  neutral: "neutral", // plain highlight, no judgment
  waiting: "waiting", // not sent yet, missing, deprioritized
});

export function toneClass(tone) {
  return `tone-${TONES[tone] || TONES.waiting}`;
}
