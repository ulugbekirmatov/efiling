/**
 * Display-only XML indent. Never hash this output — hash the packed original.
 */
export function prettyXml(xml) {
  const source = String(xml || "").trim();
  if (!source) return "";
  const tokens = source.replace(/(>)(<)(\/*)/g, "$1\n$2$3").split("\n");
  let depth = 0;
  return tokens
    .map((raw) => {
      const token = raw.trim();
      if (!token) return "";
      if (token.startsWith("</")) depth = Math.max(depth - 1, 0);
      const line = `${"  ".repeat(depth)}${token}`;
      const isOpen =
        token.startsWith("<") &&
        !token.startsWith("</") &&
        !token.startsWith("<?") &&
        !token.startsWith("<!") &&
        !token.endsWith("/>") &&
        !/<\/.+>/.test(token);
      if (isOpen) depth += 1;
      return line;
    })
    .filter(Boolean)
    .join("\n");
}

/** Strip session credentials if a payload ever contains them. Fixtures must not. */
export function redactCredentials(xml) {
  if (!xml) return xml;
  return String(xml)
    .replace(/<([A-Za-z0-9._-]*:?Assertion)\b[\s\S]*?<\/\1>/gi, "<!-- credential omitted -->")
    .replace(/<([A-Za-z0-9._-]*:?UsernameToken)\b[\s\S]*?<\/\1>/gi, "<!-- credential omitted -->")
    .replace(/<([A-Za-z0-9._-]*:?BinarySecurityToken)\b[\s\S]*?<\/\1>/gi, "<!-- credential omitted -->");
}
