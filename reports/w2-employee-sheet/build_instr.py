import json, re, sys
src, html_in, html_out = sys.argv[1], sys.argv[2], sys.argv[3]
HEADINGS = {"Notice to Employee", "Instructions for Employee",
            "Instructions for Employee (continued from back of Copy C)"}
START = re.compile(r"^(Box \d+[ab]?\.|[A-Z]{1,2}—|Note:|Future developments\.|Do you have to file\?|Earned income tax credit|"
                   r"Employee’s social security number \(SSN\)\.|Clergy and|Corrections\.|Cost of employer|Credit for excess|\(See |However, if you were)")
def paras(path):
    out, cur = [], []
    for line in open(path, encoding="utf-8").read().splitlines():
        line = line.strip()
        if not line or re.fullmatch(r"\d+", line): continue
        if line in HEADINGS or START.match(line):
            if cur: out.append({"text": " ".join(cur)}); cur = []
            if line in HEADINGS: out.append({"text": line, "heading": True}); continue
        cur.append(line)
    if cur: out.append({"text": " ".join(cur)})
    return [{**p, "text": re.sub(r"(\w)- (\w)", r"\1-\2", p["text"])} for p in out]
notice = paras(f"{src}/half5.txt")
instr = paras(f"{src}/half7.txt") + [p for p in paras(f"{src}/half9.txt") if not p.get("heading")]
# One sheet: cross-references to separate pages point at this sheet instead.
for p in notice + instr:
    p["text"] = p["text"].replace("(See also Instructions for Employee on the back of Copy C.)", "(See also Instructions for Employee on the back of Copy C of this sheet.)")
blob = json.dumps({"notice": notice, "instructions": instr}, ensure_ascii=False).replace("</", "<\\/")
open(html_out, "w", encoding="utf-8").write(open(html_in, encoding="utf-8").read().replace("INSTRUCTIONS_JSON", blob))
print(len(notice), "notice paras;", len(instr), "instruction paras;",
      sum(len(p["text"].split()) for p in notice + instr), "words")
