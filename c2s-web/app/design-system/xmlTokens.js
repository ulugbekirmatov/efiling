// Splits XML text into spans for the dark code panel. Concatenating every token's text gives
// back the input exactly, so pretty-printing and redaction stay upstream of this.
const TOKEN_PATTERN =
  /(<!--[\s\S]*?-->|<\?[\s\S]*?\?>|<!DOCTYPE[^>]*>)|(<!\[CDATA\[[\s\S]*?\]\]>)|(<\/?)([^\s>/]+)|([^\s=<>"'/]+)(\s*=\s*)("[^"]*"|'[^']*')|(\/?>)/g;

export const XML_TOKEN_CLASS = Object.freeze({
  comment: "cm",
  tag: "fn",
  attr: "kw",
  value: "str",
  punct: "cm",
  text: null,
});

export function tokenizeXml(text) {
  const source = String(text ?? "");
  const tokens = [];
  let cursor = 0;
  let insideTag = false;

  const push = (kind, value) => {
    if (value) tokens.push({ kind, text: value });
  };

  for (const match of source.matchAll(TOKEN_PATTERN)) {
    const [whole, comment, cdata, open, name, attr, equals, value, close] = match;
    const isAttribute = attr !== undefined;
    if (isAttribute && !insideTag) continue;
    if (close !== undefined && !insideTag) continue;

    push("text", source.slice(cursor, match.index));
    cursor = match.index + whole.length;

    if (comment) {
      push("comment", comment);
    } else if (cdata) {
      push("text", cdata);
    } else if (open !== undefined) {
      push("punct", open);
      push("tag", name);
      insideTag = true;
    } else if (isAttribute) {
      push("attr", attr);
      push("punct", equals);
      push("value", value);
    } else {
      push("punct", close);
      insideTag = false;
    }
  }
  push("text", source.slice(cursor));
  return tokens;
}
