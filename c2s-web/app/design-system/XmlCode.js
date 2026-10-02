import { XML_TOKEN_CLASS, tokenizeXml } from "./xmlTokens";

export default function XmlCode({ text, className = "", label = "XML" }) {
  return (
    <div className={`code ${className}`.trim()} role="region" aria-label={label} tabIndex={0}>
      <pre>
        {tokenizeXml(text).map((token, index) => {
          const tokenClass = XML_TOKEN_CLASS[token.kind];
          return tokenClass ? (
            <span key={index} className={tokenClass}>
              {token.text}
            </span>
          ) : (
            token.text
          );
        })}
      </pre>
    </div>
  );
}
