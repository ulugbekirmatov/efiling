#!/usr/bin/env bash
# SSNVS bulk verification (SSA SSNVS Handbook, January 2025). Runs com.irs.mef.ssnvs.SsnvsCli without Spring.
#   ./ssnvs.sh build   --csv <export.csv> --out <dir> [--today <yyyy-mm-dd>]
#   ./ssnvs.sh results --dir <dir>/<ein> --results <ssa-results.txt>
# Exit 0 clean, 2 operator action needed, 1 unreadable input. Output never shows an SSN, a name or a birth date.
# Compiles when a source under com/irs/mef/ssnvs is newer than the compiled CLI; works offline once ~/.m2 is filled.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
classes="$here/target/classes"
cli_class="$classes/com/irs/mef/ssnvs/SsnvsCli.class"
classpath_file="$here/target/ssnvs.classpath"

if [[ ! -f "$cli_class" ]] || [[ -n "$(find "$here/src/main/java/com/irs/mef/ssnvs" -newer "$cli_class" -print -quit)" ]]; then
  (cd "$here" && mvn -q compile) >&2
fi
if [[ ! -f "$classpath_file" || "$here/pom.xml" -nt "$classpath_file" ]]; then
  (cd "$here" && mvn -q dependency:build-classpath -Dmdep.includeScope=runtime "-Dmdep.outputFile=$classpath_file") >&2
fi
exec "$JAVA_HOME/bin/java" -cp "$classes:$(cat "$classpath_file")" com.irs.mef.ssnvs.SsnvsCli "$@"
