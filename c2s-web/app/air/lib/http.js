export function json(body, status = 200) {
  return Response.json(body, { status });
}

export function apiError(status, code, message) {
  return json({ code, message }, status);
}

export function requireSameOrigin(request) {
  const origin = request.headers.get("origin");
  const host = request.headers.get("host");
  if (!origin || !host) {
    return apiError(403, "CROSS_ORIGIN", "Origin host must match Host");
  }
  let originHost;
  try {
    originHost = new URL(origin).host;
  } catch {
    return apiError(403, "CROSS_ORIGIN", "Origin host must match Host");
  }
  if (originHost.toLowerCase() !== host.toLowerCase()) {
    return apiError(403, "CROSS_ORIGIN", "Origin host must match Host");
  }
  return null;
}

const JAVA_READY = [
  ["repoRoot", () => "AIR_REPO_ROOT"],
  ["java", () => "AIR_JAVA_HOME"],
  ["jar", () => "AIR_JAR"],
  ["pkcs12", (ready) => ready.env.pkcs12Var],
  ["passwordEnvSet", (ready) => ready.env.passwordEnv],
  ["asid", (ready) => ready.env.asidVar],
];

export function airNotConfigured(ready) {
  for (const [flag, envName] of JAVA_READY) {
    if (!ready[flag]) {
      return apiError(503, "AIR_NOT_CONFIGURED", `Set ${envName(ready)}`);
    }
  }
  return null;
}
