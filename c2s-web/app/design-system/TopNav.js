"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const LINKS = [
  { href: "/send-inspector", label: "Send inspector" },
  { href: "/scenarios", label: "Test scenarios" },
];

export default function TopNav() {
  const pathname = usePathname() || "";
  return (
    <nav className="topnav" aria-label="Primary">
      <Link href="/" className="topnav-brand">
        MeF operator
      </Link>
      <div className="topnav-links">
        {LINKS.map(({ href, label }) => (
          <Link key={href} href={href} aria-current={pathname === href || pathname.startsWith(`${href}/`) ? "page" : undefined}>
            {label}
          </Link>
        ))}
      </div>
    </nav>
  );
}
