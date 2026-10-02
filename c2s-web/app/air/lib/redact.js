// Pure text redaction for anything an AIR run shows to the browser or writes to disk.

const REDACTED = "[redacted]";
const SSN_MASK = "*****";
const SSN_VISIBLE_DIGITS = 4;
const CREDENTIAL_ELEMENTS = ["SignatureValue", "X509Certificate", "BinarySecurityToken", "Assertion", "UsernameToken"];

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
