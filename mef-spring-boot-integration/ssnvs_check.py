#!/usr/bin/env python3
"""Independent SSNVS request and result file checker.

Built from the SSA Social Security Number Verification Service (SSNVS)
Handbook, Updated January 2025,
https://www.ssa.gov/employer/ssnvsHandbook.pdf.
Submission file format is handbook pp.18-19. Returned file format is pp.20-21.
File statuses are p.24. Calendar bounds for the date of birth follow the
online-entry rules cited with the request record (handbook pp.14-15).

Usage:
  python3 ssnvs_check.py request FILE...
  python3 ssnvs_check.py result FILE...
  python3 ssnvs_check.py pair REQUEST RESULT

Each finding is "ERROR|WARN path line N Field RULE-ID: message".
A finding names the file, the line, the field, and the rule. It never
includes an SSN, a name, or a date of birth. Exit 1 when any finding
is an ERROR.
"""

import argparse
import re
import sys
from collections import defaultdict, namedtuple
from datetime import date

RECORD_LENGTH = 130
MAX_RECORDS = 250_000

# Handbook pp.18-19. Positions are 1-based and inclusive.
REQUEST_FIELDS = (
    ("SSN", 1, 9),
    ("Entry Code", 10, 12),
    ("Processing Code", 13, 15),
    ("Last Name", 16, 28),
    ("First Name", 29, 38),
    ("Middle Name", 39, 45),
    ("Date of Birth", 46, 53),
    ("Blank 54", 54, 89),
    ("User Control Data", 90, 103),
    ("Blank 104", 104, 123),
    ("Requester Identification Code", 124, 127),
    ("Multiple Request Indicator", 128, 130),
)

# Handbook pp.20-21.
RESULT_FIELDS = (
    ("SSN", 1, 9),
    ("Last Name", 10, 22),
    ("First Name", 23, 32),
    ("Middle Name", 33, 39),
    ("Date of Birth", 40, 47),
    ("Blank 48", 48, 48),
    ("Confirmation Number", 49, 83),
    ("User Control Data", 84, 97),
    ("Verification Code", 98, 98),
    ("Processing Code", 99, 101),
    ("Requester Identification Code", 102, 105),
    ("Multiple Request Indicator", 106, 108),
    ("Blank 109", 109, 120),
    ("Death Indicator", 121, 121),
    ("Blank 122", 122, 130),
)

Finding = namedtuple("Finding", "level path line field rule message")

NAME_PROBLEMS = {
    "BLANK": "may not be left blank",
    "JUSTIFY": "must be left-justified",
    "LOWER": "lowercase letters are not allowed",
    "HYPHEN": "hyphen is not allowed",
    "APOSTROPHE": "apostrophe is not allowed",
    "PERIOD": "period is not allowed",
    "SPACE": "inner space is not allowed",
    "CHAR": "only letters A-Z are allowed",
}

NUMBER_FIELD = "SSN"


def _assert_layout(fields):
    expect = 1
    for name, start, end in fields:
        if start != expect or end < start:
            raise AssertionError(f"{name} breaks the layout at {start}-{end}")
        expect = end + 1
    if expect != RECORD_LENGTH + 1:
        raise AssertionError(f"layout ends at {expect - 1}, not {RECORD_LENGTH}")


_assert_layout(REQUEST_FIELDS)
_assert_layout(RESULT_FIELDS)

REQ = {name: (start, end) for name, start, end in REQUEST_FIELDS}
RES = {name: (start, end) for name, start, end in RESULT_FIELDS}


def grab(text, layout, name):
    start, end = layout[name]
    return text[start - 1:end]


class Checker:
    def __init__(self):
        self.findings = []

    def error(self, path, line, field, rule, message):
        self.findings.append(Finding("ERROR", path, line, field, rule, message))

    def warn(self, path, line, field, rule, message):
        self.findings.append(Finding("WARN", path, line, field, rule, message))


def split_records(data):
    """Split on CRLF or LF. A trailing CR that is not part of CRLF stays in the record."""
    records = []
    endings = []
    start = 0
    index = 0
    size = len(data)
    while index < size:
        if data[index] == 0x0D and index + 1 < size and data[index + 1] == 0x0A:
            records.append(data[start:index])
            endings.append("CRLF")
            index += 2
            start = index
        elif data[index] == 0x0A:
            records.append(data[start:index])
            endings.append("LF")
            index += 1
            start = index
        else:
            index += 1
    if start < size:
        records.append(data[start:size])
        endings.append(None)
    return records, endings


def terminator_label(endings):
    used = []
    for ending in endings:
        if ending and ending not in used:
            used.append(ending)
    if not used:
        return "none"
    if len(used) == 1:
        return used[0]
    return "mixed"


def nonprintable_positions(blob):
    return [index + 1 for index, byte in enumerate(blob) if byte < 0x20 or byte > 0x7E]


def note_envelope(path, data_len, records, endings, checker):
    """File-level terminator and size rules. Return False when the file is empty."""
    if data_len == 0:
        checker.error(path, 0, "File", "FILE-EMPTY", "file is empty")
        return False
    if len(records) > MAX_RECORDS:
        checker.error(path, 0, "File", "FILE-MAX", "more than 250,000 records")
    used = {ending for ending in endings if ending}
    if len(used) > 1:
        checker.warn(path, 0, "File", "FILE-TERM-MIX", "file uses both CRLF and LF")
    if records and endings[-1] is None:
        checker.warn(path, 0, "File", "FILE-TERM-EOF", "missing a terminator after the last record")
    return True


def note_record_bytes(path, line, blob, length_rule, checker):
    """Return the record text when it is 130 characters, else None."""
    bad = nonprintable_positions(blob)
    if bad:
        checker.error(
            path, line, "File", "FILE-ASCII",
            f"{len(bad)} byte(s) outside printable ASCII, first at position {bad[0]}",
        )
    if len(blob) != RECORD_LENGTH:
        checker.error(path, line, "File", length_rule, f"length is {len(blob)}, must be 130")
        return None
    return blob.decode("latin-1")


def name_problems(raw, required):
    body = raw.rstrip(" ")
    if body == "":
        return ["BLANK"] if required else []
    problems = []
    if body[0] == " ":
        problems.append("JUSTIFY")
        body = body.lstrip(" ")
    seen = set()
    for char in body:
        if "A" <= char <= "Z":
            continue
        if "a" <= char <= "z":
            kind = "LOWER"
        elif char == "-":
            kind = "HYPHEN"
        elif char == "'":
            kind = "APOSTROPHE"
        elif char == ".":
            kind = "PERIOD"
        elif char == " ":
            kind = "SPACE"
        else:
            kind = "CHAR"
        if kind not in seen:
            seen.add(kind)
            problems.append(kind)
    return problems


def check_name(path, line, text, layout, field_name, rule_prefix, required, checker):
    for kind in name_problems(grab(text, layout, field_name), required):
        checker.error(path, line, field_name, f"{rule_prefix}-{kind}", NAME_PROBLEMS[kind])


def check_dob(path, line, raw, checker):
    # Handbook p.18: MMDDYYYY, optional. pp.14-15: a real date, not before 1800, not after today.
    if raw == " " * len(raw):
        return
    if not re.fullmatch(r"\d{8}", raw):
        checker.error(path, line, "Date of Birth", "REQ-DOB", "must be blank or MMDDYYYY")
        return
    year = int(raw[4:8])
    if year < 1800:
        checker.error(path, line, "Date of Birth", "REQ-DOB", "year is before 1800")
        return
    try:
        born = date(year, int(raw[0:2]), int(raw[2:4]))
    except ValueError:
        checker.error(path, line, "Date of Birth", "REQ-DOB", "is not a real calendar date")
        return
    if born > date.today():
        checker.error(path, line, "Date of Birth", "REQ-DOB", "is in the future")


def warn_number_shape(path, line, value, checker):
    # Handbook p.18 requires nine digits. These five shapes are numbers SSA never issues.
    area, group, serial = value[0:3], value[3:5], value[5:9]
    if area == "000":
        checker.warn(path, line, NUMBER_FIELD, "REQ-SSN-AREA-000", "SSA never issues area 000")
    elif area == "666":
        checker.warn(path, line, NUMBER_FIELD, "REQ-SSN-AREA-666", "SSA never issues area 666")
    elif area >= "900":
        checker.warn(path, line, NUMBER_FIELD, "REQ-SSN-AREA-900", "SSA never issues area 900-999")
    if group == "00":
        checker.warn(path, line, NUMBER_FIELD, "REQ-SSN-GROUP-00", "SSA never issues group 00")
    if serial == "0000":
        checker.warn(path, line, NUMBER_FIELD, "REQ-SSN-SERIAL-0000", "SSA never issues serial 0000")


def check_literal(path, line, text, layout, field_name, expected, rule, checker):
    if grab(text, layout, field_name) != expected:
        checker.error(path, line, field_name, rule, f"must be {expected}")


def check_spaces(path, line, text, layout, field_name, display, rule, checker):
    if grab(text, layout, field_name).strip(" ") != "":
        checker.error(path, line, display, rule, "must be spaces")


def check_request(path, records, checker):
    seen_number = {}
    seen_control = {}
    for line, blob in enumerate(records, start=1):
        text = note_record_bytes(path, line, blob, "REQ-LEN", checker)
        if text is None:
            continue
        number = grab(text, REQ, NUMBER_FIELD)
        if not re.fullmatch(r"\d{9}", number):
            checker.error(path, line, NUMBER_FIELD, "REQ-SSN", "must be nine digits")
        else:
            warn_number_shape(path, line, number, checker)
            if number in seen_number:
                checker.warn(
                    path, line, NUMBER_FIELD, "REQ-SSN-DUP",
                    f"duplicate of line {seen_number[number]}",
                )
            else:
                seen_number[number] = line
        check_literal(path, line, text, REQ, "Entry Code", "TPV", "REQ-TPV", checker)
        check_literal(path, line, text, REQ, "Processing Code", "214", "REQ-214", checker)
        check_name(path, line, text, REQ, "Last Name", "REQ-LAST", True, checker)
        check_name(path, line, text, REQ, "First Name", "REQ-FIRST", True, checker)
        check_name(path, line, text, REQ, "Middle Name", "REQ-MIDDLE", False, checker)
        check_dob(path, line, grab(text, REQ, "Date of Birth"), checker)
        check_spaces(path, line, text, REQ, "Blank 54", "Blank", "REQ-FILL-54", checker)
        control = grab(text, REQ, "User Control Data")
        if control.strip(" ") == "":
            checker.warn(
                path, line, "User Control Data", "REQ-CTRL-BLANK",
                "blank. A unique identifier is recommended",
            )
        elif any(ord(char) < 0x20 or ord(char) > 0x7E for char in control):
            checker.error(path, line, "User Control Data", "REQ-CTRL-ASCII", "must be printable ASCII")
        elif control in seen_control:
            checker.error(
                path, line, "User Control Data", "REQ-CTRL-DUP",
                f"same non-blank value as line {seen_control[control]}",
            )
        else:
            seen_control[control] = line
        check_spaces(path, line, text, REQ, "Blank 104", "Blank", "REQ-FILL-104", checker)
        check_literal(path, line, text, REQ, "Requester Identification Code", "OEVS", "REQ-OEVS", checker)
        check_literal(path, line, text, REQ, "Multiple Request Indicator", "000", "REQ-MRI", checker)


def check_result_number(path, line, number, code, checker):
    # Handbook pp.20-21 and p.24: mask the first five positions when the code is blank, 3, or 6.
    plain = re.fullmatch(r"\d{9}", number) is not None
    masked = re.fullmatch(r"XXXXX\d{4}", number) is not None
    if code in (" ", "3", "6"):
        if not masked:
            checker.error(
                path, line, NUMBER_FIELD, "RES-SSN-MASK",
                "five X characters plus four digits are required for code blank, 3, or 6",
            )
    elif code in ("1", "5"):
        if not plain:
            checker.error(
                path, line, NUMBER_FIELD, "RES-SSN-MASK",
                "nine unmasked digits are required for code 1 or 5",
            )
    elif masked:
        checker.error(
            path, line, NUMBER_FIELD, "RES-SSN-MASK",
            "masking is allowed only for code blank, 3, or 6",
        )
    elif not plain:
        checker.error(
            path, line, NUMBER_FIELD, "RES-SSN-MASK",
            "must be nine digits, or five X characters plus four digits for code blank, 3, or 6",
        )


def check_result(path, records, checker):
    confirmations = []
    for line, blob in enumerate(records, start=1):
        text = note_record_bytes(path, line, blob, "RES-LEN", checker)
        if text is None:
            continue
        code = grab(text, RES, "Verification Code")
        check_result_number(path, line, grab(text, RES, NUMBER_FIELD), code, checker)
        if grab(text, RES, "Blank 48") != " ":
            checker.error(path, line, "Blank", "RES-BLANK-48", "must be blank")
        confirmation = grab(text, RES, "Confirmation Number")
        confirmations.append(confirmation)
        if confirmation.strip(" ") == "":
            checker.warn(path, line, "Confirmation Number", "RES-CONFIRM-BLANK", "may not be blank")
        if code not in (" ", "1", "3", "5", "6"):
            checker.error(
                path, line, "Verification Code", "RES-CODE",
                "must be blank, 1, 3, 5, or 6. Codes 2 and 4 were pre-2011 gender codes",
            )
        check_literal(path, line, text, RES, "Processing Code", "214", "RES-214", checker)
        check_literal(path, line, text, RES, "Requester Identification Code", "OEVS", "RES-OEVS", checker)
        check_literal(path, line, text, RES, "Multiple Request Indicator", "000", "RES-MRI", checker)
        check_spaces(path, line, text, RES, "Blank 109", "Blank", "RES-FILL-109", checker)
        death = grab(text, RES, "Death Indicator")
        if death == " ":
            checker.warn(path, line, "Death Indicator", "RES-DEATH-BLANK", "blank, expected Y or N")
        elif death not in ("Y", "N"):
            checker.error(path, line, "Death Indicator", "RES-DEATH", "must be Y or N")
        check_spaces(path, line, text, RES, "Blank 122", "Blank", "RES-FILL-122", checker)
    if len(set(confirmations)) > 1:
        checker.warn(
            path, 0, "Confirmation Number", "RES-CONFIRM-DIFF",
            "is not identical on every record",
        )


def result_tally(records):
    codes = {"blank": 0, "1": 0, "3": 0, "5": 0, "6": 0, "other": 0}
    deaths = {"Y": 0, "N": 0, "blank": 0, "other": 0}
    for blob in records:
        if len(blob) != RECORD_LENGTH:
            continue
        text = blob.decode("latin-1")
        code = grab(text, RES, "Verification Code")
        if code == " ":
            codes["blank"] += 1
        elif code in ("1", "3", "5", "6"):
            codes[code] += 1
        else:
            codes["other"] += 1
        death = grab(text, RES, "Death Indicator")
        if death == " ":
            deaths["blank"] += 1
        elif death in ("Y", "N"):
            deaths[death] += 1
        else:
            deaths["other"] += 1
    code_part = " ".join(f"{key}={codes[key]}" for key in ("blank", "1", "3", "5", "6", "other"))
    death_part = " ".join(f"{key}={deaths[key]}" for key in ("Y", "N", "blank", "other"))
    return [
        f"tally verification-code: {code_part}",
        f"tally death-indicator: {death_part}",
    ]


def check_pair(request_path, result_path, request_records, result_records, checker):
    # Handbook p.18: user control 90-103 returns in positions 84-97. pp.20-21: name and DOB are echoed.
    if len(request_records) != len(result_records):
        checker.error(
            result_path, 0, "File", "PAIR-COUNT",
            f"request has {len(request_records)} records and result has {len(result_records)} records",
        )
    req_map = defaultdict(list)
    res_map = defaultdict(list)
    for line, blob in enumerate(request_records, start=1):
        if len(blob) == RECORD_LENGTH:
            text = blob.decode("latin-1")
            req_map[grab(text, REQ, "User Control Data")].append((line, text))
    for line, blob in enumerate(result_records, start=1):
        if len(blob) == RECORD_LENGTH:
            text = blob.decode("latin-1")
            res_map[grab(text, RES, "User Control Data")].append((line, text))

    echo_fields = ("Last Name", "First Name", "Middle Name", "Date of Birth")
    for control, group in res_map.items():
        matches = req_map.get(control, [])
        if len(matches) == 0:
            for line, _text in group:
                checker.error(
                    result_path, line, "User Control Data", "PAIR-ORPHAN",
                    "no request record has this user control value",
                )
            continue
        if len(matches) != 1 or len(group) != 1:
            for line, _text in group:
                checker.error(
                    result_path, line, "User Control Data", "PAIR-NOT-ONE",
                    "is not paired with exactly one request record",
                )
            continue
        _request_line, request_text = matches[0]
        line, result_text = group[0]
        for field_name in echo_fields:
            if grab(request_text, REQ, field_name) != grab(result_text, RES, field_name):
                checker.error(
                    result_path, line, field_name, "PAIR-ECHO",
                    "echoed value differs from the request record",
                )
    for control, group in req_map.items():
        if control not in res_map:
            for line, _text in group:
                checker.error(
                    request_path, line, "User Control Data", "PAIR-ORPHAN",
                    "no result record has this user control value",
                )
        elif len(group) != 1:
            for line, _text in group:
                checker.error(
                    request_path, line, "User Control Data", "PAIR-NOT-ONE",
                    "is not paired with exactly one result record",
                )


def sorted_findings(findings):
    return sorted(findings, key=lambda item: (item.line, item.rule, item.field))


def has_error(findings):
    return any(item.level == "ERROR" for item in findings)


def emit(header, findings, terminator, tallies=None):
    print(f"== {header}")
    if terminator is not None:
        print(f"terminator: {terminator}")
    for item in sorted_findings(findings):
        print(f"{item.level} {item.path} line {item.line} {item.field} {item.rule}: {item.message}")
    if tallies:
        for line in tallies:
            print(line)
    if not findings:
        print("OK")
    else:
        errors = sum(1 for item in findings if item.level == "ERROR")
        warnings = len(findings) - errors
        print(f"{errors} error(s), {warnings} warning(s)")


def open_records(path):
    """Return (records, endings, byte_length, read_findings)."""
    try:
        with open(path, "rb") as handle:
            data = handle.read()
    except OSError as exc:
        reason = exc.strerror or "unreadable"
        finding = Finding("ERROR", path, 0, "File", "FILE-READ", f"cannot read file ({reason})")
        return None, None, 0, [finding]
    records, endings = split_records(data)
    return records, endings, len(data), []


def report_request(path, records, endings, byte_length):
    checker = Checker()
    if note_envelope(path, byte_length, records, endings, checker):
        check_request(path, records, checker)
    emit(path, checker.findings, terminator_label(endings))
    return checker.findings


def report_result(path, records, endings, byte_length):
    checker = Checker()
    tallies = []
    if note_envelope(path, byte_length, records, endings, checker):
        check_result(path, records, checker)
        tallies = result_tally(records)
    emit(path, checker.findings, terminator_label(endings), tallies)
    return checker.findings


def run_request(path):
    records, endings, size, failed = open_records(path)
    if failed:
        emit(path, failed, "none")
        return failed, None
    return report_request(path, records, endings, size), records


def run_result(path):
    records, endings, size, failed = open_records(path)
    if failed:
        emit(path, failed, "none")
        return failed, None
    return report_result(path, records, endings, size), records


def run_pair(request_path, result_path, request_records, result_records):
    checker = Checker()
    if request_records is None or result_records is None:
        return checker.findings
    check_pair(request_path, result_path, request_records, result_records, checker)
    emit("pair", checker.findings, None)
    return checker.findings


def build_parser():
    parser = argparse.ArgumentParser(
        prog="ssnvs_check.py",
        description=(
            "Check SSA SSNVS request files, result files, or one request/result pair. "
            "Spec: SSNVS Handbook, Updated January 2025, submission pp.18-19, "
            "returned file pp.20-21, statuses p.24."
        ),
    )
    commands = parser.add_subparsers(dest="command", required=True)
    request = commands.add_parser("request", help="check 130-character SSNVS submission files")
    request.add_argument("files", nargs="+", metavar="FILE")
    result = commands.add_parser("result", help="check 130-character SSNVS result files")
    result.add_argument("files", nargs="+", metavar="FILE")
    pair = commands.add_parser("pair", help="match a result file back to its request file")
    pair.add_argument("request", metavar="REQUEST")
    pair.add_argument("result", metavar="RESULT")
    return parser


def main(argv=None):
    args = build_parser().parse_args(argv)
    errored = False
    if args.command == "request":
        for path in args.files:
            findings, _records = run_request(path)
            errored = errored or has_error(findings)
    elif args.command == "result":
        for path in args.files:
            findings, _records = run_result(path)
            errored = errored or has_error(findings)
    else:
        request_findings, request_records = run_request(args.request)
        result_findings, result_records = run_result(args.result)
        pair_findings = run_pair(args.request, args.result, request_records, result_records)
        errored = has_error(request_findings) or has_error(result_findings) or has_error(pair_findings)
    return 1 if errored else 0


if __name__ == "__main__":
    sys.exit(main())
