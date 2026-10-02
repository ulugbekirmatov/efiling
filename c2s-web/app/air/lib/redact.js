const REDACTED = "[redacted]";
const SSN_MASK = "*****";
const SSN_VISIBLE_DIGITS = 4;
const CREDENTIAL_ELEMENTS = ["SignatureValue", "X509Certificate", "KeyIdentifier", "BinarySecurityToken", "Assertion", "UsernameToken"];

const NINE_DIGIT_RUN = /(?<!\d)(?:\d{9}|\d{3}-\d{2}-\d{4})(?!\d)/g;

const SSN_ELEMENT = /(<(?:[\w.-]+:)?SSN(?:\s[^>]*)?(?<!\/)>)([^<]*)(<\/(?:[\w.-]+:)?SSN\s*>)/g;

// The lookbehind skips self-closing tags so `<ds:Assertion/>` cannot swallow a later closing tag.
const CREDENTIAL_ELEMENT = new RegExp(
  `<((?:[\\w.-]+:)?(?:${CREDENTIAL_ELEMENTS.join("|")}))((?:\\s[^>]*)?)(?<!/)>[\\s\\S]*?</\\1\\s*>`,
  "g",
);

function maskedDigits(value) {
  const digits = value.replace(/\D/g, "");
  return digits.length >= SSN_VISIBLE_DIGITS ? SSN_MASK + digits.slice(-SSN_VISIBLE_DIGITS) : SSN_MASK;
}

export function maskSsn(text) {
  if (typeof text !== "string") return text;
  return text.replace(SSN_ELEMENT, (match, open, value, close) => (value === "" ? match : open + maskedDigits(value) + close));
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
