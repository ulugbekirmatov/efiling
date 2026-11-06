"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { PAGES } from "../registry";

const GROUPS = [];
for (const page of PAGES) {
  const found = GROUPS.find((group) => group.label === page.group);
  if (found) found.links.push(page);
  else GROUPS.push({ label: page.group, links: [page] });
}

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
        {GROUPS.map((group) => (
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
