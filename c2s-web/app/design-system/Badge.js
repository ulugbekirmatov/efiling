import { toneClass } from "./tones";

export function Badge({ tone, children }) {
  return <span className={`badge ${toneClass(tone)}`}>{children}</span>;
}

export function Chip({ tone, children }) {
  return <span className={tone ? `chip ${toneClass(tone)}` : "chip"}>{children}</span>;
}
