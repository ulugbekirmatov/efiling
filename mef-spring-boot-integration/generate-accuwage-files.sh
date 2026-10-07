#!/usr/bin/env bash
# Writes the TY2026 AccuWage test files to reports/accuwage-ty2026/ at the repo root.
# Optional: ACCUWAGE_SUBMITTER_EIN and ACCUWAGE_BSO_USER_ID stamp the real RA submitter values.
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
out="$(cd .. && pwd)/reports/accuwage-ty2026"
rm -rf "$out"
args=(-q test -Dtest=AccuWageFilesTest "-Daccuwage.out=$out")
[[ -n "${ACCUWAGE_SUBMITTER_EIN:-}" ]] && args+=("-Daccuwage.submitterEin=$ACCUWAGE_SUBMITTER_EIN")
[[ -n "${ACCUWAGE_BSO_USER_ID:-}" ]] && args+=("-Daccuwage.bsoUserId=$ACCUWAGE_BSO_USER_ID")
mvn "${args[@]}"
echo "$out"
ls -1 "$out"
