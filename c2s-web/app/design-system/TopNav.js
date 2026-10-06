"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const LINKS = [
  {
    label: "MeF",
    links: [
      { href: "/send-inspector", label: "Send inspector" },
      { href: "/scenarios", label: "Test scenarios" },
    ],
  },
  {
    label: "AIR",
    links: [
      { href: "/air/transmissions", label: "Transmissions" },
      { href: "/air/scenarios", label: "AATS scenarios" },
    ],
  },
  {
    label: "FTB",
    links: [
      { href: "/ftb/scenarios", label: "Test scenarios" },
      { href: "/ftb/operator-page", label: "Operator page PR" },
    ],
  },
];

function isCurrent(pathname, href) {
  return pathname === href || pathname.startsWith(`${href}/`);
}

export default function TopNav() {
  const pathname = usePathname() || "";
  return (
    <nav className="topnav" aria-label="Primary">
      <Link href="/" className="topnav-brand">
        C2S operator
      </Link>
      <div className="topnav-links">
        {LINKS.map((group) => (
          <div key={group.label} className="topnav-group">
            <span className="topnav-group-label">{group.label}</span>
            {group.links.map(({ href, label }) => (
              <Link key={href} href={href} aria-current={isCurrent(pathname, href) ? "page" : undefined}>
                {label}
              </Link>
            ))}
          </div>
        ))}
      </div>
    </nav>
  );
}
