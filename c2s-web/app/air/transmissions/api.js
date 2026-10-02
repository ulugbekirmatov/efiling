import { parseApiError, isUnreachableError } from "../../send-inspector/api.js";

export { parseApiError, isUnreachableError };

async function request(path, options) {
  let response;
  try {
    response = await fetch(path, { cache: "no-store", ...options });
  } catch (cause) {
    throw parseApiError(0, { message: cause.message || "Network error" }, false);
  }

  const text = await response.text();
  let body = null;
  let json = false;
  if (text) {
    try {
      body = JSON.parse(text);
      json = true;
    } catch {
      body = { message: text.slice(0, 200) };
    }
  }

  if (!response.ok) {
    throw parseApiError(response.status, body, json);
  }
  return body;
}

function runPath(id, suffix) {
  const base = `/air/api/runs/${encodeURIComponent(id)}`;
  return suffix ? `${base}/${suffix}` : base;
}

export function getReadiness() {
  return request("/air/api/readiness");
}

export function listRuns() {
  return request("/air/api/runs");
}

export function getRun(id) {
  return request(runPath(id));
}

export function validateRun(id) {
  return request(runPath(id, "validate"), { method: "POST" });
}

export function previewRun(id) {
  return request(runPath(id, "preview"), { method: "POST" });
}

export function submitRun(id, confirm) {
  return request(runPath(id, "submit"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ confirm }),
  });
}

export function checkRunStatus(id) {
  return request(runPath(id, "status"), { method: "POST" });
}
