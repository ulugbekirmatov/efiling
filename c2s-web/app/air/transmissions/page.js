import { Suspense } from "react";
import AirTransmissions from "./AirTransmissions";
import "./air-transmissions.css";

export const metadata = {
  title: "AIR Transmissions",
};

export default function TransmissionsPage() {
  return (
    <div className="page">
      <header className="page-head">
        <p className="eyebrow">AIR operator</p>
        <h1>Transmissions</h1>
        <p className="intro">
          Pick a run to read the form, manifest, what we sent, and the IRS answer.
        </p>
      </header>
      <Suspense fallback={<p className="muted">Loading…</p>}>
        <AirTransmissions />
      </Suspense>
    </div>
  );
}
