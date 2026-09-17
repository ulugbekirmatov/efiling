#!/bin/bash
# Smoke-test the running app's certificate diagnostics and IRS ATS login over HTTP.
# Reads every identifier from .env in this module directory (MEF_ETIN, MEF_EFIN, MEF_ASID, MEF_KEYSTORE_*);
# nothing is hardcoded here. Login is certificate-only, so the request carries no body.
#
# Usage: ./test-mef-login.sh            (builds, starts the app, runs cert → login → status → logout → status, stops the app)
#        ./test-mef-login.sh --no-build (app already built)
set -euo pipefail

MODULE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_URL="http://localhost:8080/api"
STARTUP_WAIT_SECONDS=20
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:/opt/homebrew/bin:$PATH"

cd "$MODULE_DIR"

[ -f .env ] || { echo ".env not found in $MODULE_DIR (see CLAUDE.md, Configuration)" >&2; exit 1; }
if grep -qE '^MEF_KEYSTORE_PASSWORD=$' .env; then
  echo "MEF_KEYSTORE_PASSWORD is empty in .env; run ../create-pkcs12.sh and fill it in" >&2
  exit 1
fi

java_major="$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}' | cut -d. -f1)"
[ "$java_major" = "17" ] || { echo "Java 17 required, found $java_major (Metro breaks on 21+)" >&2; exit 1; }

if [ "${1:-}" != "--no-build" ]; then
  echo "Building..."
  mvn -q clean package -DskipTests
fi

echo "Starting the app (log: logs/mef-spring-boot.log)..."
mvn -q spring-boot:run > /dev/null 2>&1 &
APP_PID=$!
trap 'echo; echo "Stopping app (pid $APP_PID)"; kill "$APP_PID" 2>/dev/null || true' EXIT

for _ in $(seq 1 "$STARTUP_WAIT_SECONDS"); do
  curl -sf "$BASE_URL/mef/auth/status" > /dev/null 2>&1 && break
  sleep 1
done

echo; echo "== 1. Certificate diagnostics (keystore, alias, subject) =="
curl -s "$BASE_URL/mef/auth/test-certificate" | jq '.'

echo; echo "== 2. IRS ATS login (certificate-only; ETIN/ASID from .env) =="
curl -s "$BASE_URL/mef/auth/login" | jq '.'

echo; echo "== 3. Session status =="
curl -s "$BASE_URL/mef/auth/status" | jq '.'

echo; echo "== 4. IRS logout (LogoutClient; must appear as /a2a/mef/Logout in a2a_sdk.log.*) =="
curl -s "$BASE_URL/mef/auth/logout" | jq '.'

echo; echo "== 5. Session status after logout (loggedIn must be false) =="
curl -s "$BASE_URL/mef/auth/status" | jq '.'

echo
echo "If login failed, the real SOAP fault is in a2a_sdk.log.* in this directory, not in the JSON above."
