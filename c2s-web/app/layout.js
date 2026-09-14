import "./globals.css";

export const metadata = {
  title: "MeF Send Inspector",
  description: "Read Return XML and SendSubmissions MIME for a captured send",
};

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
