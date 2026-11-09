#!/usr/bin/env bash
# Model-checks FilingDesk.tla with TLC. Offline once the TLA+ tools jar is cached.
#
#   check.sh            each invariant on the model of the code; compares with EXPECTED
#   check.sh mutants    each broken variant must fail its invariant; the fix sketch must pass
#   check.sh trace <Invariant> [Mutation]
#                       one run with the full TLC output (the counterexample, if any)
#
# Exit 0: results match EXPECTED (and every mutant is caught).
# Exit 1: a result differs from EXPECTED, or a mutant was not caught.
# Exit 2: usage or tooling error.
# Exit 3: results match, but covered code changed since the skill's sync commit.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
SKILL="$ROOT/.claude/skills/verify-filing-desk/SKILL.md"

TLA_VERSION=1.7.4
TLA_URL="https://github.com/tlaplus/tlaplus/releases/download/v${TLA_VERSION}/tla2tools.jar"
TLA_SHA256=936a262061c914694dfd669a543be24573c45d5aa0ff20a8b96b23d01e050e88
TLA_JAR="${TLA_JAR:-$HOME/.cache/tla/tla2tools-${TLA_VERSION}.jar}"
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
JAVA="$JAVA_HOME/bin/java"
TOOLING_EXIT=2
DRIFT_EXIT=3

# Expected result per invariant on the model of the current code.
# FAIL rows are open findings; see the skill's Findings section.
EXPECTED=(
  "TypeOK PASS"
  "SameBytesFiledAtMostOnce PASS"
  "OneLiveReturnPerPeriod FAIL"
  "HeldNeverReleased PASS"
  "HeldIsJournaled PASS"
)

# Broken variant -> the invariant it must break.
MUTANTS=(
  "reserveSkipsClaim SameBytesFiledAtMostOnce"
  "unknownTransportReleases HeldNeverReleased"
  "reserveNotDurable HeldIsJournaled"
)
FIX_SKETCH=periodGuardInsideReserve

die() { echo "check.sh: $*" >&2; exit "$TOOLING_EXIT"; }

require_tools() {
  [[ -x "$JAVA" ]] || die "Java 17 not found at $JAVA_HOME (brew install openjdk@17)"
  if [[ ! -f "$TLA_JAR" ]]; then
    die "TLA+ tools missing at $TLA_JAR. Fetch them once:
  mkdir -p \"$(dirname "$TLA_JAR")\" && curl -fL -o \"$TLA_JAR\" $TLA_URL"
  fi
  if [[ -n "$TLA_SHA256" ]]; then
    local actual
    actual="$(shasum -a 256 "$TLA_JAR" | cut -d' ' -f1)"
    [[ "$actual" == "$TLA_SHA256" ]] || die "$TLA_JAR sha256 is $actual, expected $TLA_SHA256"
  fi
}

WORK="$(mktemp -d "${TMPDIR:-/tmp}/filing-desk-tlc.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

# run_tlc <label> <mutation> <invariant>... -> prints PASS, or the name of the
# violated invariant (TLC stops at the first). Full output in $WORK/<label>-<mutation>.out
run_tlc() {
  local label="$1" mutation="$2" cfg out violated
  shift 2
  cfg="$WORK/$label-$mutation.cfg"
  out="$WORK/$label-$mutation.out"
  sed -e "s/^\( *Mutation = \).*/\1\"$mutation\"/" -e '/^INVARIANTS/,$d' "$HERE/FilingDesk.cfg" >"$cfg"
  printf 'INVARIANT %s\n' "$@" >>"$cfg"
  cp "$HERE/FilingDesk.tla" "$WORK/FilingDesk.tla"
  set +e
  ( cd "$WORK" && "$JAVA" -XX:+UseParallelGC -cp "$TLA_JAR" tlc2.TLC \
      -workers auto -deadlock -metadir "$WORK/meta-$label-$mutation" \
      -config "$cfg" FilingDesk.tla ) >"$out" 2>&1
  set -e
  violated="$(sed -n 's/^Error: Invariant \([A-Za-z0-9_]*\) is violated\.$/\1/p' "$out" | head -1)"
  if [[ -n "$violated" ]]; then
    echo "$violated"
  elif grep -q "Model checking completed. No error has been found." "$out"; then
    echo PASS
  else
    cat "$out" >&2
    die "TLC did not finish for $label ($mutation)"
  fi
}

states_line() { grep -Eo '[0-9,]+ distinct states found' "$1" | tail -1 || true; }

drift() {
  local sync files changed
  sync="$(sed -n 's/^sync-commit: *\([0-9a-f]\{7,40\}\).*/\1/p' "$SKILL" | head -1)"
  files="$(sed -n '/<!-- coverage:begin -->/,/<!-- coverage:end -->/p' "$SKILL" \
    | grep -Eo '`mef-spring-boot-integration/[^`]+\.java`' | tr -d '`' | sort -u)"
  [[ -n "$sync" && -n "$files" ]] || die "no sync-commit or coverage list in $SKILL"
  # shellcheck disable=SC2086
  changed="$(cd "$ROOT" && git diff --name-only "$sync" -- $files)"
  if [[ -n "$changed" ]]; then
    echo
    echo "STALE: covered code changed since sync commit $sync:"
    sed 's/^/  /' <<<"$changed"
    echo "Re-read those changes against FilingDesk.tla before trusting these results."
    return 1
  fi
  echo
  echo "In sync: no covered file changed since $sync."
}

# Invariants expected to PASS share one TLC run; each expected FAIL runs alone.
check_code() {
  local mismatch=0 row invariant expected got passing=()
  for row in "${EXPECTED[@]}"; do
    read -r invariant expected <<<"$row"
    [[ "$expected" == PASS ]] && passing+=("$invariant")
  done
  got="$(run_tlc expected-pass none "${passing[@]}")"
  printf '%-28s %-8s %s\n' "${passing[*]}" PASS "$(states_line "$WORK/expected-pass-none.out")"
  if [[ "$got" != PASS ]]; then
    echo "  -> $got is violated (TLC stops at the first violation)"
    mismatch=1
  else
    echo "  -> PASS"
  fi
  for row in "${EXPECTED[@]}"; do
    read -r invariant expected <<<"$row"
    [[ "$expected" == FAIL ]] || continue
    got="$(run_tlc "$invariant" none "$invariant")"
    printf '%-28s %-8s %s\n' "$invariant" FAIL "$(states_line "$WORK/$invariant-none.out")"
    if [[ "$got" == "$invariant" ]]; then
      echo "  -> FAIL, as expected (open finding)"
    else
      echo "  -> $got: the open finding no longer reproduces"
      mismatch=1
    fi
  done
  if [[ "$mismatch" -ne 0 ]]; then
    echo "MISMATCH: read the counterexample with: check.sh trace <Invariant>"
    return 1
  fi
}

check_mutants() {
  local missed=0 row mutation invariant got all=()
  for row in "${MUTANTS[@]}"; do
    read -r mutation invariant <<<"$row"
    got="$(run_tlc "$invariant" "$mutation" "$invariant")"
    printf 'mutant %-26s must break %-26s -> %s\n' "$mutation" "$invariant" \
      "$([[ "$got" == "$invariant" ]] && echo caught || echo SURVIVED)"
    [[ "$got" == "$invariant" ]] || missed=1
  done
  for row in "${EXPECTED[@]}"; do
    read -r invariant _ <<<"$row"
    all+=("$invariant")
  done
  got="$(run_tlc all "$FIX_SKETCH" "${all[@]}")"
  printf 'fix    %-26s must keep every invariant -> %s\n' "$FIX_SKETCH" "$got"
  [[ "$got" == PASS ]] || missed=1
  if [[ "$missed" -ne 0 ]]; then
    echo "A mutant survived or the fix sketch fails: the model no longer guards that code."
    return 1
  fi
}

require_tools
case "${1:-}" in
  "")
    status=0
    check_code || status=1
    if [[ "$status" -eq 0 ]] && ! drift; then
      status="$DRIFT_EXIT"
    fi
    exit "$status"
    ;;
  mutants)
    check_mutants
    ;;
  trace)
    [[ $# -ge 2 ]] || die "usage: check.sh trace <Invariant> [Mutation]"
    got="$(run_tlc "$2" "${3:-none}" "$2")"
    cat "$WORK/$2-${3:-none}.out"
    echo "RESULT: $2 (${3:-none}) $([[ "$got" == PASS ]] && echo PASS || echo FAIL)"
    ;;
  *)
    die "usage: check.sh [mutants | trace <Invariant> [Mutation]]"
    ;;
esac
