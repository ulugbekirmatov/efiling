"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { parseApiError } from "../../../send-inspector/api";

async function composeRun(scenarioId) {
  const response = await fetch("/air/api/runs", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ scenarioId }),
  });
  const body = await response.json().catch(() => null);
  if (!response.ok) throw parseApiError(response.status, body);
  return body;
}

export default function ComposeButton({ scenarioId }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  async function onCompose() {
    setBusy(true);
    setError(null);
    try {
      const run = await composeRun(scenarioId);
      router.push(`/air/transmissions?id=${run.id}`);
    } catch (err) {
      setError(err.code ? `${err.code}: ${err.message}` : err.message);
      setBusy(false);
    }
  }

  return (
    <div className="air-scenario-compose">
      <button type="button" className="btn btn-primary" onClick={onCompose} disabled={busy}>
        {busy ? "Composing…" : "Compose run"}
      </button>
      {error ? (
        <p role="alert" className="callout attention">
          {error}
        </p>
      ) : null}
    </div>
  );
}
