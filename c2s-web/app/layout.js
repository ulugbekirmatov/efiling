import "./design-system/tokens.css";
import "./design-system/components.css";
import TopNav from "./design-system/TopNav";

export const metadata = {
  title: "MeF operator",
  description: "Operator pages for MeF sends and ATS test scenarios",
};

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body>
        <TopNav />
        <main>{children}</main>
      </body>
    </html>
  );
}
