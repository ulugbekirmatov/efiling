import { Suspense } from "react";
import SendInspector from "./SendInspector";

export const metadata = {
  title: "MeF Send Inspector",
};

export default function SendInspectorPage() {
  return (
    <Suspense fallback={<p style={{ padding: "28px 20px", color: "#5b677a" }}>Loading…</p>}>
      <SendInspector />
    </Suspense>
  );
}
