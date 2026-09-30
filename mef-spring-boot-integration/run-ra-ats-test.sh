#!/bin/bash
# Send one TY2026 94x ATS scenario to IRS ATS as a Reporting Agent (ReportingAgentAtsScenarioTest).
# Scenarios: 1 Orchid 941, 2 Marigold 941 + Schedule B, 3 Daffodil 941 + Schedule R + 8974,
# 4 Orchid 941-X. Reads identifiers from .env in this module directory; MEF_SOFTWARE_ID must be
# 8 digits from the process environment or .env. This script never writes .env.
#
# Usage: MEF_SOFTWARE_ID=12345678 ./run-ra-ats-test.sh <scenario 1-4>
set -euo pipefail

TEST_CLASS=ReportingAgentAtsScenarioTest
SCENARIO="${1:-}"
case "$SCENARIO" in
  1|2|3|4) ;;
  *)
    echo "Usage: $0 <scenario 1-4> (got '${SCENARIO}')" >&2
    exit 1
    ;;
esac

MODULE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
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

if [ -z "${MEF_SOFTWARE_ID:-}" ]; then
  MEF_SOFTWARE_ID=$(grep -E '^MEF_SOFTWARE_ID=' .env | tail -n1 | cut -d= -f2- || true)
fi
case "${MEF_SOFTWARE_ID:-}" in
  [0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]) ;;
  *)
    echo "MEF_SOFTWARE_ID is not set (8 digits). ATS accepted placeholder 12345678 for the OneWell run; export MEF_SOFTWARE_ID=<value> to run." >&2
    exit 1
    ;;
esac
export MEF_SOFTWARE_ID

if pgrep -f "$TEST_CLASS" >/dev/null 2>&1; then
  echo "$TEST_CLASS is already running; a leftover IRS session would collide with concurrent-session limits" >&2
  exit 1
fi
if lsof -nP -iTCP:8080 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Something is listening on :8080; stop it before this test (a killed run leaves an IRS session open)" >&2
  exit 1
fi

JOURNAL=data/newsend-journal.jsonl
JOURNAL_BEFORE=0
if [ -f "$JOURNAL" ]; then
  JOURNAL_BEFORE=$(wc -l < "$JOURNAL" | tr -d ' ')
fi

MVN_EXIT=0
mvn -q test -Dtest="$TEST_CLASS" -Dmef.ats.scenario="$SCENARIO" -Dmef.integration.test.enabled=true -Dsurefire.failIfNoSpecifiedTests=false || MVN_EXIT=$?

REPORT=target/surefire-reports/com.irs.mef.reportingagent.$TEST_CLASS.txt
OUTPUT=target/surefire-reports/com.irs.mef.reportingagent.$TEST_CLASS-output.txt

echo
echo "== surefire report =="
if [ -f "$REPORT" ]; then
  cat "$REPORT"
fi

echo
echo "== RA ATS stdout =="
if [ -f "$OUTPUT" ]; then
  grep '^RA ATS ' "$OUTPUT" || true
fi

echo
echo "== new journal rows (after $JOURNAL_BEFORE) =="
if [ -f "$JOURNAL" ]; then
  tail -n +$((JOURNAL_BEFORE + 1)) "$JOURNAL" | cut -c1-300
fi

TODAY=$(date +%F)
APP_LOG=logs/mef-spring-boot.log
echo
echo "== app log $TODAY =="
if [ -f "$APP_LOG" ]; then
  grep "$TODAY" "$APP_LOG" | grep -E 'TRANSMITTED|VALIDATION ERROR|Acknowledgment retrieved|Rejected|Indeterminate|ERROR' | cut -c1-400 || true
fi

echo
echo "Real SOAP faults are in a2a_sdk.log.* in this directory."
exit "$MVN_EXIT"
