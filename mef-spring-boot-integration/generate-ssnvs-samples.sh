#!/usr/bin/env bash
# Writes synthetic SSNVS sample files to reports/ssnvs-samples/ at the repo root, then checks them with
# ssnvs_check.py, which is written from the SSA handbook alone and shares no code with the Java writer.
# Every SSN, name and birth date in the samples is synthetic (SsnvsFixtures).
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
out="$(cd .. && pwd)/reports/ssnvs-samples"
rm -rf "$out"
mvn -q test -Dtest=SsnvsSampleFilesTest "-Dssnvs.out=$out"
echo "$out"
ls -1 "$out"
python3 ssnvs_check.py request "$out/ssnvs-request.txt"
python3 ssnvs_check.py result "$out/ssnvs-result.txt"
python3 ssnvs_check.py pair "$out/ssnvs-request.txt" "$out/ssnvs-result.txt"
