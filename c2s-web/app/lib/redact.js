// The one place that masks personal identifiers and strips session credentials before anything
// reaches the browser. Every server page, route handler, and catalog redacts through this module;
// redact.guard.test.js fails on a second copy anywhere under app/.
const REDACTED = "[redacted]";
const SSN_MASK = "*****";
const SSN_VISIBLE_DIGITS = 4;
const CREDENTIAL_ELEMENTS = ["SignatureValue", "X509Certificate", "KeyIdentifier", "BinarySecurityToken", "Assertion", "UsernameToken"];

const NINE_DIGIT_RUN = /(?<!\d)(?:\d{9}|\d{3}-\d{2}-\d{4})(?!\d)/g;

// Any element whose local name ends in SSN (SSN, EmployeeSSN, ...), with or without a prefix.
const SSN_ELEMENT = /(<((?:[\w.-]+:)?[\w.-]*SSN)(?:\s[^>]*)?(?<!\/)>)([^<]*)(<\/\2\s*>)/g;

// The lookbehind skips self-closing tags so `<ds:Assertion/>` cannot swallow a later closing tag.
const CREDENTIAL_ELEMENT = new RegExp(
  `<((?:[\\w.-]+:)?(?:${CREDENTIAL_ELEMENTS.join("|")}))((?:\\s[^>]*)?)(?<!/)>[\\s\\S]*?</\\1\\s*>`,
  "g",
);

export function maskedDigits(value) {
  const digits = String(value).replace(/\D/g, "");
  return digits.length >= SSN_VISIBLE_DIGITS ? SSN_MASK + digits.slice(-SSN_VISIBLE_DIGITS) : SSN_MASK;
}

export function maskSsn(text) {
  if (typeof text !== "string") return text;
  return text.replace(SSN_ELEMENT, (match, open, name, value, close) => (value === "" ? match : open + maskedDigits(value) + close));
}

// For text that quotes a known person's SSN outside any element (narratives, check messages).
// Masks only the given values, plain or dashed, so EINs in the same text stay readable.
export function maskKnownSsns(text, ssns) {
  if (typeof text !== "string") return text;
  return (ssns || []).reduce((out, raw) => {
    const ssn = String(raw).replace(/\D/g, "");
    if (ssn === "") return out;
    const dashed = `${ssn.slice(0, 3)}-${ssn.slice(3, 5)}-${ssn.slice(5)}`;
    return out.split(ssn).join(maskedDigits(ssn)).split(dashed).join(maskedDigits(ssn));
  }, text);
}

export function redactCredentials(text) {
  if (typeof text !== "string") return text;
  return text.replace(CREDENTIAL_ELEMENT, (match, name, attributes) => `<${name}${attributes}>${REDACTED}</${name}>`);
}

export function redactSecrets(text, secretValues) {
  if (typeof text !== "string") return text;
  const secrets = (secretValues || [])
    .filter((value) => typeof value === "string" && value !== "")
    .sort((a, b) => b.length - a.length);
  return secrets.reduce((result, secret) => result.split(secret).join(REDACTED), text);
}

export function redactDocument(text) {
  return redactCredentials(maskSsn(text));
}

// For stderr and error strings, which can quote an SSN outside any element. Not for XML documents,
// where a bare nine-digit run is usually an EIN that must stay intact.
export function redactMessage(text, secretValues) {
  if (typeof text !== "string") return text;
  const withoutSecrets = redactDocument(redactSecrets(text, secretValues));
  return withoutSecrets.replace(NINE_DIGIT_RUN, (run) => maskedDigits(run));
}
