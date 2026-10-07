#!/usr/bin/env bash
# Writes the AccuWage test files to reports/accuwage-ty<year>/ at the repo root.
# Usage: ./generate-accuwage-files.sh [tax-year]   (default 2026; 2025 for AccuWage before it accepts TY2026)
# Optional: ACCUWAGE_SUBMITTER_EIN and ACCUWAGE_BSO_USER_ID stamp the real RA submitter values.
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
year="${1:-2026}"
out="$(cd .. && pwd)/reports/accuwage-ty$year"
rm -rf "$out"
args=(-q test -Dtest=AccuWageFilesTest "-Daccuwage.taxYear=$year" "-Daccuwage.out=$out")
[[ -n "${ACCUWAGE_SUBMITTER_EIN:-}" ]] && args+=("-Daccuwage.submitterEin=$ACCUWAGE_SUBMITTER_EIN")
[[ -n "${ACCUWAGE_BSO_USER_ID:-}" ]] && args+=("-Daccuwage.bsoUserId=$ACCUWAGE_BSO_USER_ID")
mvn "${args[@]}"
echo "$out"
ls -1 "$out"
# Independent second opinion: efw2_check.py is built from the TY2026 publication only, not from the Java writer.
if [[ "$year" == 2026 ]]; then
  python3 efw2_check.py "$out"/0*.txt
fi
