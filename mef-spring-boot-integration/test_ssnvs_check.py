#!/usr/bin/env python3
"""Black-box tests for ssnvs_check.py.

Records are built here. The number uses an issued-looking shape, area 123
and group 45, and the names are DOE and JANE. Findings must not repeat them.
"""

import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent / "ssnvs_check.py"
RECORD = 130


def issued(area="123", group="45", serial="6789"):
    value = f"{area}{group}{serial}"
    if len(value) != 9 or not value.isdigit():
        raise AssertionError("test number must be nine digits")
    return value


def put(rec, start, text):
    raw = text.encode("ascii")
    end = start - 1 + len(raw)
    if end > len(rec):
        raise AssertionError(f"field at {start} overflows the record")
    rec[start - 1:end] = raw


def request_record(number=None, last="DOE", first="JANE", middle="QUINN", dob="01011990",
                   control="C0000000000001"):
    rec = bytearray(b" " * RECORD)
    put(rec, 1, issued() if number is None else number)
    put(rec, 10, "TPV")
    put(rec, 13, "214")
    put(rec, 16, last)
    put(rec, 29, first)
    put(rec, 39, middle)
    put(rec, 46, dob)
    put(rec, 90, control)
    put(rec, 124, "OEVS")
    put(rec, 128, "000")
    text = rec.decode("ascii")
    if len(text) != RECORD:
        raise AssertionError(len(text))
    return text


def result_record(number=None, last="DOE", first="JANE", middle="QUINN", dob="01011990",
                  control="C0000000000001", code=" ", death="N", confirm="AB12CD34EF56GH78"):
    if number is None:
        plain = issued()
        number = ("XXXXX" + plain[5:]) if code in (" ", "3", "6") else plain
    rec = bytearray(b" " * RECORD)
    put(rec, 1, number)
    put(rec, 10, last)
    put(rec, 23, first)
    put(rec, 33, middle)
    put(rec, 40, dob)
    put(rec, 49, confirm)
    put(rec, 84, control)
    if code != " ":
        put(rec, 98, code)
    put(rec, 99, "214")
    put(rec, 102, "OEVS")
    put(rec, 106, "000")
    if death != " ":
        put(rec, 121, death)
    text = rec.decode("ascii")
    if len(text) != RECORD:
        raise AssertionError(len(text))
    return text


def dump(directory, name, lines):
    body = b"\r\n".join(line.encode("ascii") for line in lines)
    if lines:
        body += b"\r\n"
    path = Path(directory) / name
    path.write_bytes(body)
    return str(path)


def secrets():
    primary = issued()
    other = issued(serial="7680")
    return (
        primary,
        other,
        primary[5:],
        "XXXXX" + primary[5:],
        "DOE",
        "doe",
        "JANE",
        "jane",
        "QUINN",
        "01011990",
        "02302020",
        "DOE-SMITH",
        "SMITH",
        "JOHN",
    )


class SsnvsCheckTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)

    def run_check(self, *args):
        return subprocess.run(
            [sys.executable, str(SCRIPT), *args],
            capture_output=True,
            text=True,
        )

    def write(self, name, lines):
        return dump(self.tmp.name, name, lines)

    def assert_no_values(self, proc, *extra):
        blob = proc.stdout + proc.stderr
        for secret in list(secrets()) + list(extra):
            self.assertNotIn(secret, blob)

    def assert_fail(self, proc, rule, *extra):
        self.assertEqual(proc.returncode, 1, proc.stdout + proc.stderr)
        self.assertIn(rule, proc.stdout)
        self.assert_no_values(proc, *extra)

    def test_help(self):
        proc = self.run_check("--help")
        self.assertEqual(proc.returncode, 0, proc.stderr)
        self.assertIn("request", proc.stdout)
        self.assertIn("result", proc.stdout)
        self.assertIn("pair", proc.stdout)

    def test_clean_request_exits_zero(self):
        path = self.write("request.txt", [request_record()])
        proc = self.run_check("request", path)
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertIn("terminator: CRLF", proc.stdout)
        self.assertIn("OK", proc.stdout)
        self.assertNotIn("ERROR", proc.stdout)
        self.assertNotIn("WARN", proc.stdout)
        self.assert_no_values(proc)

    def test_short_record(self):
        path = self.write("short.txt", [request_record()[:129]])
        proc = self.run_check("request", path)
        self.assert_fail(proc, "REQ-LEN")
        self.assertNotIn("REQ-TPV", proc.stdout)

    def test_lowercase_last_name(self):
        path = self.write("lower.txt", [request_record(last="doe")])
        proc = self.run_check("request", path)
        self.assert_fail(proc, "REQ-LAST-LOWER")

    def test_hyphenated_last_name(self):
        path = self.write("hyphen.txt", [request_record(last="DOE-SMITH")])
        proc = self.run_check("request", path)
        self.assert_fail(proc, "REQ-LAST-HYPHEN")

    def test_blank_first_name(self):
        path = self.write("blank-first.txt", [request_record(first=" " * 10)])
        proc = self.run_check("request", path)
        self.assert_fail(proc, "REQ-FIRST-BLANK")

    def test_wrong_tpv(self):
        rec = bytearray(request_record().encode("ascii"))
        put(rec, 10, "BAD")
        path = self.write("tpv.txt", [rec.decode("ascii")])
        proc = self.run_check("request", path)
        self.assert_fail(proc, "REQ-TPV")

    def test_bad_dob(self):
        path = self.write("dob.txt", [request_record(dob="02302020")])
        proc = self.run_check("request", path)
        self.assert_fail(proc, "REQ-DOB")

    def test_nonblank_filler(self):
        rec = bytearray(request_record().encode("ascii"))
        put(rec, 54, "X")
        path = self.write("filler.txt", [rec.decode("ascii")])
        proc = self.run_check("request", path)
        self.assert_fail(proc, "REQ-FILL-54")

    def test_duplicate_control(self):
        lines = [
            request_record(control="C0000000000001"),
            request_record(number=issued(serial="7680"), control="C0000000000001"),
        ]
        path = self.write("dup-control.txt", lines)
        proc = self.run_check("request", path)
        self.assert_fail(proc, "REQ-CTRL-DUP")

    def test_duplicate_number_warns(self):
        lines = [
            request_record(control="C0000000000001"),
            request_record(control="C0000000000002"),
        ]
        path = self.write("dup-number.txt", lines)
        proc = self.run_check("request", path)
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertIn("REQ-SSN-DUP", proc.stdout)
        self.assertNotIn("REQ-NAME-DUP", proc.stdout)
        self.assertNotIn("ERROR", proc.stdout)
        self.assert_no_values(proc)

    def test_never_issued_shapes_warn(self):
        cases = (
            ("000", "45", "7680", "REQ-SSN-AREA-000"),
            ("666", "45", "7680", "REQ-SSN-AREA-666"),
            ("900", "45", "7680", "REQ-SSN-AREA-900"),
            ("123", "00", "7680", "REQ-SSN-GROUP-00"),
            ("123", "45", "0000", "REQ-SSN-SERIAL-0000"),
        )
        for area, group, serial, rule in cases:
            number = issued(area, group, serial)
            with self.subTest(rule=rule):
                path = self.write(f"shape-{rule}.txt", [request_record(number=number)])
                proc = self.run_check("request", path)
                self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
                self.assertIn(rule, proc.stdout)
                self.assertNotIn("ERROR", proc.stdout)
                self.assert_no_values(proc, number)

    def test_clean_result_exits_zero(self):
        path = self.write("result.txt", [result_record()])
        proc = self.run_check("result", path)
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertIn("OK", proc.stdout)
        self.assertIn("tally verification-code: blank=1 1=0 3=0 5=0 6=0 other=0", proc.stdout)
        self.assertIn("tally death-indicator: Y=0 N=1 blank=0 other=0", proc.stdout)
        self.assertNotIn("ERROR", proc.stdout)
        self.assert_no_values(proc)

    def test_result_code_5_masked_number(self):
        masked = "XXXXX" + issued()[5:]
        path = self.write("code5.txt", [result_record(number=masked, code="5")])
        proc = self.run_check("result", path)
        self.assert_fail(proc, "RES-SSN-MASK")

    def test_result_code_7(self):
        path = self.write("code7.txt", [result_record(number=issued(), code="7")])
        proc = self.run_check("result", path)
        self.assert_fail(proc, "RES-CODE")
        self.assertIn("pre-2011", proc.stdout)

    def test_pair_orphan(self):
        request = self.write("request.txt", [request_record(control="C0000000000001")])
        result = self.write("result.txt", [result_record(control="C0000000000002")])
        proc = self.run_check("pair", request, result)
        self.assert_fail(proc, "PAIR-ORPHAN")

    def test_pair_match(self):
        request = self.write("request.txt", [request_record()])
        result = self.write("result.txt", [result_record()])
        proc = self.run_check("pair", request, result)
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertNotIn("PAIR-", proc.stdout)
        self.assert_no_values(proc)

    def test_pair_echo_mismatch(self):
        request = self.write("request.txt", [request_record()])
        result = self.write("result.txt", [result_record(first="JOHN")])
        proc = self.run_check("pair", request, result)
        self.assert_fail(proc, "PAIR-ECHO")

    def test_pair_last_four_must_match_every_code(self):
        # Handbook p.20: positions 6-9 stay visible for every verification code.
        base = issued()
        bad_tail = "0001"
        for code in (" ", "1", "3", "5", "6", "7"):
            label = "blank" if code == " " else code
            if code in (" ", "3", "6"):
                shown = "XXXXX" + bad_tail
            else:
                shown = base[:5] + bad_tail
            with self.subTest(code=label):
                request = self.write(f"request-{label}.txt", [request_record()])
                result = self.write(
                    f"result-{label}.txt",
                    [result_record(number=shown, code=code)],
                )
                proc = self.run_check("pair", request, result)
                self.assert_fail(proc, "PAIR-SSN4", shown, bad_tail)
                self.assertIn(
                    "SSN PAIR-SSN4: positions 6-9 differ from the request record",
                    proc.stdout,
                )
                if code in ("1", "5"):
                    self.assertIn("PAIR-SSN9", proc.stdout)
                else:
                    self.assertNotIn("PAIR-SSN9", proc.stdout)

    def test_pair_codes_1_and_5_require_all_nine_digits(self):
        # Handbook pp.20-21: codes 1 and 5 are never masked.
        base = issued()
        for code in ("1", "5"):
            with self.subTest(code=code, match=False):
                shown = "99999" + base[5:]
                request = self.write(f"request-nine-{code}.txt", [request_record()])
                result = self.write(
                    f"result-nine-{code}.txt",
                    [result_record(number=shown, code=code)],
                )
                proc = self.run_check("pair", request, result)
                self.assert_fail(proc, "PAIR-SSN9", shown)
                self.assertIn(
                    "SSN PAIR-SSN9: nine digits differ from the request record",
                    proc.stdout,
                )
                self.assertNotIn("PAIR-SSN4", proc.stdout)
            with self.subTest(code=code, match=True):
                request = self.write(f"request-nine-ok-{code}.txt", [request_record(number=base)])
                result = self.write(
                    f"result-nine-ok-{code}.txt",
                    [result_record(number=base, code=code)],
                )
                proc = self.run_check("pair", request, result)
                self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
                self.assertNotIn("PAIR-SSN", proc.stdout)
                self.assert_no_values(proc)

    def test_mixed_confirmation_is_error(self):
        # Handbook p.20: one confirmation number is assigned to a file.
        other = issued(serial="7680")
        first = "AB12CD34EF56GH78"
        second = "ZZ99YY88XX77WW66"
        path = self.write("mixed-confirm.txt", [
            result_record(confirm=first),
            result_record(
                number="XXXXX" + other[5:],
                control="C0000000000002",
                confirm=second,
            ),
        ])
        proc = self.run_check("result", path)
        self.assertEqual(proc.returncode, 1, proc.stdout + proc.stderr)
        self.assertIn(
            "ERROR " + path + " line 0 Confirmation Number RES-CONFIRM-DIFF: "
            "is not identical on every record",
            proc.stdout,
        )
        self.assertNotIn("WARN", proc.stdout)
        self.assert_no_values(proc, first, second, other)

    def test_same_first_and_last_name_different_number_warns(self):
        # Handbook p.15: SSA may not process identical first and last names.
        other = issued(serial="7680")
        path = self.write("same-name.txt", [
            request_record(control="C0000000000001"),
            request_record(number=other, middle="ANNE", control="C0000000000002"),
        ])
        proc = self.run_check("request", path)
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertIn(
            "WARN " + path + " line 2 Last Name REQ-NAME-DUP: "
            "same first and last name as line 1",
            proc.stdout,
        )
        self.assertEqual(proc.stdout.count("REQ-NAME-DUP"), 1)
        self.assertNotIn("ERROR", proc.stdout)
        self.assert_no_values(proc, other, "ANNE")

    def test_modes_do_not_print_fixed_record_values(self):
        area, group, serial = "135", "79", "2468"
        number = issued(area, group, serial)
        other_serial = "0001"
        other = issued(area, group, other_serial)
        last = "ALDER"
        first = "BIRCH"
        middle = "CEDAR"
        born = "07041776"
        wrong_tail = "9999"
        request = self.write("request.txt", [
            request_record(
                number=number, last=last, first=first, middle=middle, dob=born,
                control="C0000000000001",
            ),
            request_record(
                number=other, last=last, first=first, middle=middle, dob=born,
                control="C0000000000002",
            ),
        ])
        unmasked = "99999" + other[5:]
        masked = "XXXXX" + wrong_tail
        result = self.write("result.txt", [
            result_record(
                number=masked, last=last, first=first, middle=middle, dob=born,
                control="C0000000000001", code=" ", confirm="AB12CD34EF56GH78",
            ),
            result_record(
                number=unmasked, last=last, first=first, middle=middle, dob=born,
                control="C0000000000002", code="5", confirm="ZZ99YY88XX77WW66",
            ),
        ])
        procs = (
            self.run_check("request", request),
            self.run_check("result", result),
            self.run_check("pair", request, result),
        )
        banned = (number, other, unmasked, masked, last, first, middle, born)
        tails = (serial, other_serial, wrong_tail)
        for proc in procs:
            self.assertIn(proc.returncode, (0, 1), proc.stdout + proc.stderr)
            blob = proc.stdout + proc.stderr
            blob = blob.replace(request, "").replace(result, "").replace(self.tmp.name, "")
            for secret in banned:
                self.assertNotIn(secret, blob)
            for tail in tails:
                self.assertIsNone(
                    re.search(r"(?<!\d)" + tail + r"(?!\d)", blob),
                    tail + " appeared as its own token",
                )


if __name__ == "__main__":
    unittest.main()
