import Link from "next/link";
import "./globals.css";

export const metadata = {
  title: "MeF operator",
  description: "Operator pages for MeF sends and ATS test scenarios",
};

const nav = {
  bar: {
    display: "flex",
    flexWrap: "wrap",
    alignItems: "center",
    gap: "10px 20px",
    padding: "10px 20px",
    background: "#fff",
    borderBottom: "1px solid #d8dee8",
    fontSize: 14,
  },
  brand: {
    fontWeight: 650,
    color: "#274869",
    marginRight: 8,
  },
  links: {
    display: "flex",
    flexWrap: "wrap",
    gap: 16,
  },
};

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body>
        <nav style={nav.bar}>
          <span style={nav.brand}>MeF operator</span>
          <div style={nav.links}>
            <Link href="/send-inspector">Send inspector</Link>
            <Link href="/scenarios">Test scenarios</Link>
          </div>
        </nav>
        {children}
      </body>
    </html>
  );
}
