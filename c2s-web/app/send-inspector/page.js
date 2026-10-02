import { Suspense } from "react";
import SendInspector from "./SendInspector";
import "./send-inspector.css";

export const metadata = {
  title: "MeF Send Inspector",
};

export default function SendInspectorPage() {
  return (
    <Suspense fallback={<p className="page muted">Loading…</p>}>
      <SendInspector />
    </Suspense>
  );
}
