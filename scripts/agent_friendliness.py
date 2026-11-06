"""Measure how agent-friendly the MeF repo is. Rerunnable; prints a table and a JSON blob.
Usage: python3 -I agent_friendliness.py <repo root> [--json out.json]
Metrics are facts an agent trips on: prose rules it must hold in its head, doc references that no longer
resolve, duplicate ways to do one thing, dead paths it would copy, and how much of the rule table is enforced."""
import os, re, sys, json, subprocess, glob
root = sys.argv[1]
J = os.path.join(root, "mef-spring-boot-integration")
def sh(cmd):
    return subprocess.run(cmd, shell=True, cwd=root, capture_output=True, text=True).stdout
def loc(paths):
    n = 0
    for p in paths:
        for f in glob.glob(os.path.join(p, "**/*.java"), recursive=True):
            n += sum(1 for _ in open(f, errors="replace"))
    return n
main = os.path.join(J, "src/main/java/com/irs/mef")
legacy = [os.path.join(main, d) for d in ("controller", "service", "dto", "config", "exception")]
current = [os.path.join(main, d) for d in ("filing", "web", "inspector", "ack")]
m = {}
m["loc_legacy"] = loc(legacy); m["loc_current"] = loc(current)
src_text = sh("git grep -h '' -- 'mef-spring-boot-integration/src' 'c2s-web/app' 'c2s-web/*.js' '*.sh' 2>/dev/null")
# 1. stale doc symbols: backticked CamelCase identifiers in markdown that no source file mentions
md_files = [f for f in sh("git ls-files -- '*.md' ':!Version_1*' ':!94x-2026' ':!c2s/**' ':!sdk-reference/**' ':!**/node_modules/**'").split() ]
stale = {}
EXEMPT = re.compile(r"deleted|retired|historical|superseded|no longer", re.I)
def live_lines(f):
    return "\n".join(l for l in open(os.path.join(root, f), errors="replace") if not EXEMPT.search(l))
for f in md_files:
    txt = live_lines(f)
    for sym in set(re.findall(r"`([A-Z][A-Za-z0-9]{3,})(?:\.[a-z][A-Za-z0-9]*)?`", txt)):
        if sym in ("TODO", "README", "CLAUDE", "IRS", "ATS", "PRD", "MTOM", "SAML", "SOAP", "HTTP", "JSON", "XML", "WSDL", "PKCS12"): continue
        if sym not in src_text and (sym.endswith(("Test","Service","Controller","Gate","Preflight","Config","Store","Builder","Factory","Generator","Guard")) or sh(f"git log --all --oneline -S{sym} -- '*.java' '*.js' | head -1").strip()):
            stale.setdefault(sym, []).append(f)
m["stale_doc_symbols"] = len(stale)
# 2. doc paths that don't exist
missing_paths = {}
for f in md_files:
    txt = live_lines(f)
    for p in set(re.findall(r"`([A-Za-z0-9_./-]+\.(?:java|sh|md|yml|xml|xsd|js|json|properties|tsv))(?::\d+(?:-\d+)?)?`", txt)):
        cands = [os.path.join(root, p), os.path.join(root, os.path.dirname(f), p), os.path.join(J, p)]
        if not any(os.path.exists(c) for c in cands):
            # last resort: basename exists anywhere tracked?
            if sh(f"git ls-files -- '**/{os.path.basename(p)}' '{os.path.basename(p)}'").strip() == "":
                missing_paths.setdefault(p, []).append(f)
m["doc_paths_missing"] = len(missing_paths)
# 3. prose rules an agent must hold
claude_mds = [f for f in md_files if os.path.basename(f) == "CLAUDE.md"]
rules = 0; claude_words = 0
for f in claude_mds:
    for line in open(os.path.join(root, f), errors="replace"):
        claude_words += len(line.split())
        if re.search(r"\b(never|don'?t|do not|must not|always|⚠️|do not trust)\b", line, re.I): rules += 1
m["claude_md_files"] = len(claude_mds); m["claude_md_words"] = claude_words; m["prose_rule_lines"] = rules
# 4. rule table enforcement
table = open(os.path.join(root, "CLAUDE.md"), errors="replace").read().split("## Rules and what enforces them")[-1]
rows = [l for l in table.splitlines() if l.startswith("| ") and not l.startswith("| Rule") and not l.startswith("|---")]
m["rule_rows"] = len(rows); m["rule_rows_docs_only"] = sum(1 for r in rows if re.search(r"Docs only|Not enforced|judgment", r))
# 5. dead paths an agent would copy
m["todo_or_commented_sdk_calls"] = len(re.findall(r"TODO|// .*Client\.invoke", sh(f"git grep -h -E 'TODO|^\\s*//.*Client\\.invoke' -- 'mef-spring-boot-integration/src/main'")))
m["duplicate_private_helpers"] = len([k for k, v in __import__("collections").Counter(re.findall(r"private \w+ (\w+)\(", sh("git grep -h -E 'private \\w+ \\w+\\(' -- 'mef-spring-boot-integration/src/main/java/com/irs/mef/service'"))).items() if v > 1])
m["http_routes_legacy"] = len(re.findall(r"@(Get|Post|Put|Delete)Mapping", sh("git grep -h -E '@(Get|Post|Put|Delete)Mapping' -- 'mef-spring-boot-integration/src/main/java/com/irs/mef/controller'")))
m["http_routes_current"] = len(re.findall(r"@(Get|Post|Put|Delete)Mapping", sh("git grep -h -E '@(Get|Post|Put|Delete)Mapping' -- 'mef-spring-boot-integration/src/main/java/com/irs/mef/web' 'mef-spring-boot-integration/src/main/java/com/irs/mef/inspector'")))
# 6. tests per generation
tests = sh("git ls-files -- 'mef-spring-boot-integration/src/test/**/*.java'").split()
def imports(pkgs):
    return sum(1 for t in tests if re.search(r"import com\.irs\.mef\.(" + "|".join(pkgs) + r")\.", open(os.path.join(root, t), errors="replace").read()) or any(f"/{p}/" in t for p in pkgs))
m["test_files"] = len(tests); m["test_files_touching_legacy"] = imports(["service", "controller", "dto"]); m["test_files_touching_current"] = imports(["filing", "web", "inspector", "ack"])
# 7. onewell identifiers outside the guard's reach (docs)
m["onewell_ids_in_md"] = len(sh("git grep -n -E '97661|238689|23868900' -- '*.md' ':!CLAUDE.md'").splitlines())
print(f"{'metric':38s} value"); [print(f"{k:38s} {v}") for k, v in m.items()]
print("\nstale doc symbols:", ", ".join(sorted(stale)) or "none")
print("missing doc paths:", ", ".join(sorted(missing_paths)) or "none")
if "--json" in sys.argv: json.dump({"metrics": m, "stale_doc_symbols": stale, "doc_paths_missing": missing_paths}, open(sys.argv[sys.argv.index("--json")+1], "w"), indent=1)
