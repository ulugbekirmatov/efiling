#!/usr/bin/env bash
# Checks every fenced JavaScript block in c2s/*.md the way the C2S platform would:
# the block must parse (node --check) and, unless tagged `browser`, must be Nashorn ES5
# with no transaction-control SQL. Tag a block `js nocheck` to show a deliberately wrong example.
set -euo pipefail

DOCS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

ES5_FORBIDDEN='\bconst\b|\blet\b|=>|`'
TX_CONTROL='\b(BEGIN|COMMIT|ROLLBACK|SAVEPOINT|START TRANSACTION)\b'

failures=0
blocks=0

for doc in "$DOCS_DIR"/*.md; do
	awk -v out="$WORK_DIR" -v doc="$(basename "$doc" .md)" '
		{ sub(/\r$/, "") }
		/^```(js|javascript)/ && !inblock { inblock = 1; n++; info = $0; sub(/^```/, "", info); start = NR; body = ""; next }
		/^```/ && inblock { inblock = 0; file = out "/" doc "-" n ".js"; printf "%s", body > file; close(file); printf "%s\t%d\t%s\n", file, start, info; next }
		inblock { body = body $0 "\n" }
	' "$doc"
done > "$WORK_DIR/index.tsv"

while IFS=$'\t' read -r snippet line info; do
	blocks=$((blocks + 1))
	label="$(basename "$snippet" .js | sed 's/-[0-9]*$//').md:$line"
	case " $info " in *" nocheck "*) continue ;; esac

	if ! node --check "$snippet" 2> "$WORK_DIR/err"; then
		echo "SYNTAX  $label"; sed 's/^/        /' "$WORK_DIR/err" | head -5
		failures=$((failures + 1)); continue
	fi
	case " $info " in *" browser "*) continue ;; esac

	stripped="$(sed -E 's://.*$::' "$snippet" | grep -vE '^[[:space:]]*(\*|/\*)' || true)"
	if hit="$(printf '%s\n' "$stripped" | grep -nE "$ES5_FORBIDDEN" | head -3)"; then
		echo "ES5     $label"; printf '%s\n' "$hit" | sed 's/^/        /'
		failures=$((failures + 1))
	fi
	if hit="$(printf '%s\n' "$stripped" | grep -niE "$TX_CONTROL" | head -3)"; then
		echo "TXSQL   $label"; printf '%s\n' "$hit" | sed 's/^/        /'
		failures=$((failures + 1))
	fi
done < "$WORK_DIR/index.tsv"

echo "checked $blocks blocks, $failures failures"
[[ "$failures" -eq 0 ]]
