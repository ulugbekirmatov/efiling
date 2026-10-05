export function parseApiError(status, body, json) {
  const rawCode = body == null ? null : body.code ?? body.errorCode;
  const code = rawCode != null ? String(rawCode) : null;
  const message =
    (body && body.message) ||
    (status ? `Request failed (${status})` : "Request failed");
  const err = new Error(message);
  err.status = status || 0;
  err.code = code;
  err.json = typeof json === "boolean" ? json : body != null;
  return err;
}

export function isUnreachableError(err) {
  if (!err) return false;
  const status = err.status;
  if (status == null || status === 0) return true;
  return status >= 500 && err.json !== true;
}

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

export function listSends() {
  return request("/backend/inspector/sends");
}

export function getSend(id) {
  return request(`/backend/inspector/sends/${encodeURIComponent(id)}`);
}

export function fetchAck(id) {
  return request(`/backend/inspector/sends/${encodeURIComponent(id)}/ack`, {
    method: "POST",
  });
}

export function getSession() {
  return request("/backend/mef/auth/status");
}

export function login() {
  return request("/backend/mef/auth/login");
}

export function logout() {
  return request("/backend/mef/auth/logout");
}
