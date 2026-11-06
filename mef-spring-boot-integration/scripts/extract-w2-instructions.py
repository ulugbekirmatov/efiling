#!/usr/bin/env python3
"""Turn the employee back text of the IRS Form W-2 PDF into the JSON the W-2 employee sheet prints.

Usage: extract-w2-instructions.py <fw2.pdf> <out.json>

fw2.pdf prints each copy twice per page, so only the top half of pages 5, 7 and 9 (the backs of
Copies B, C and 2) is read. Pub 1141 2.3.19 lets us edit the text to fit, so the cross-references
between separate copies are rewritten to point at this one sheet. Needs pdftotext (poppler).
"""
import json
import re
import subprocess
import sys

BACK_OF = {"notice": 5, "instructions": 7, "continued": 9}
TOP_HALF = ["-x", "0", "-y", "0", "-W", "612", "-H", "396"]
HEADINGS = {"Notice to Employee", "Instructions for Employee"}
DROPPED = {
    "Instructions for Employee (continued from back of Copy C)",
    "Box 12 (continued)",
    "(continued on back of Copy 2)",
}
LEAD = re.compile(
    r"^(Box \d+[ab]?\.|[A-Z]{1,2}—|Note:|Future developments\.|Do you have to file\?|"
    r"Earned income tax credit \(EITC\)\.|Employee’s social security number \(SSN\)\.|"
    r"Clergy and religious workers\.|Corrections\.|"
    r"Cost of employer-sponsored health coverage \(if such cost is provided by the employer\)\.|"
    r"Credit for excess taxes\.)"
)
# Matched per line, so a lead that wraps onto a second line is matched by its first words.
PARAGRAPH_START = re.compile(LEAD.pattern[:-1] + r"|Cost of employer-sponsored|\(See |However, if you were)")
SHEET_REFERENCES = {
    "(See also Instructions for Employee on the back of Copy C.)":
        "(See also Instructions for Employee on the back of Copy C of this sheet.)",
    "(See also Notice to Employee on the back of Copy B.)":
        "(See also Notice to Employee on the back of Copy B of this sheet.)",
}


def page_lines(pdf, page):
    text = subprocess.run(
        ["pdftotext", "-f", str(page), "-l", str(page), *TOP_HALF, pdf, "-"],
        check=True, capture_output=True, text=True,
    ).stdout
    return [line.strip() for line in text.splitlines()]


def paragraphs(lines):
    out, current = [], []

    def flush():
        if current:
            out.append(" ".join(current))
            current.clear()

    for line in lines:
        if not line or line in DROPPED or re.fullmatch(r"\d+", line):
            continue
        if line in HEADINGS:
            flush()
            out.append({"heading": line})
            continue
        if PARAGRAPH_START.match(line):
            flush()
        current.append(line)
    flush()
    return [p if isinstance(p, dict) else paragraph(p) for p in out]


def paragraph(text):
    # pdftotext keeps the hyphen of a word broken across lines ("1040- SR"); rejoin it.
    text = re.sub(r"(\w)- (\w)", r"\1-\2", text)
    text = SHEET_REFERENCES.get(text, text)
    lead = LEAD.match(text)
    if lead:
        return {"lead": lead.group(1), "text": text[lead.end():].lstrip()}
    return {"text": text}


def main(pdf, out):
    notice = paragraphs(page_lines(pdf, BACK_OF["notice"]))
    instructions = paragraphs(page_lines(pdf, BACK_OF["instructions"]) + page_lines(pdf, BACK_OF["continued"]))
    with open(out, "w", encoding="utf-8") as f:
        json.dump({"source": "IRS Form W-2 (2026), fw2.pdf pages 5, 7 and 9, top copy",
                   "notice": notice, "instructions": instructions}, f, ensure_ascii=False, indent=1)
        f.write("\n")
    words = sum(len((p.get("lead", "") + " " + p.get("text", p.get("heading", ""))).split())
                for p in notice + instructions)
    print(f"{len(notice)} notice paragraphs, {len(instructions)} instruction paragraphs, {words} words")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
