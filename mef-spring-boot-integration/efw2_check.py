#!/usr/bin/env python3
"""Independent EFW2 (W-2 wage file) checker for tax year 2026.

Built from SSA Publication 42-007, EFW2 Tax Year 2026 (version 2, 9/14/2026),
https://www.ssa.gov/employer/efw/26efw2.pdf, and the AccuWage Online issue
levels SSA publishes at https://www.ssa.gov/employer/accuwage/.

Usage: python3 efw2_check.py [--tax-year 2025] FILE...
--tax-year 2025 swaps in the TY2025 wage base and maximum only. The TY2026 fields
that TY2025 reserved (RE 319-328, RW 490-495, the new RO/RU codes) are optional, so a
TY2025 file that leaves them blank checks the same way.
Prints every finding as "ERROR|WARN record-number record-id positions: message",
then OK or a count per file. Exits 1 when any file has an ERROR.
"""

import re
import sys
from collections import namedtuple

TAX_YEAR = 2026
WAGE_BASES_CENTS = {2025: 176_100_00, 2026: 184_500_00}
RECORD_LENGTH = 512
RECORD_DELIMITER = b"\r\n"
SOCIAL_SECURITY_WAGE_BASE_CENTS = WAGE_BASES_CENTS[TAX_YEAR]
SOCIAL_SECURITY_MAX_TAX_CENTS = 11_439_00
SOCIAL_SECURITY_RATE = 0.062
MEDICARE_RATE = 0.0145
ADDITIONAL_MEDICARE_RATE = 0.009
ADDITIONAL_MEDICARE_THRESHOLD_CENTS = 200_000_00
HOUSEHOLD_COVERAGE_MINIMUM_CENTS = 3_000_00
TAX_ROUNDING_TOLERANCE_CENTS = 100
MAX_RW_PER_SUBMISSION = 1_000_000
MAX_RE_PER_SUBMISSION = 50_000

SUBMITTER_EIN_BAD_PREFIXES = {"07", "08", "09", "17", "18", "19", "28", "29", "49", "69", "70", "78", "79", "89"}
EMPLOYER_EIN_BAD_PREFIXES = SUBMITTER_EIN_BAD_PREFIXES | {"00"}

STATE_ABBREVIATIONS = {
    "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "DC", "FL", "GA", "HI", "ID", "IL", "IN", "IA", "KS",
    "KY", "LA", "ME", "MD", "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ", "NM", "NY", "NC",
    "ND", "OH", "OK", "OR", "PA", "RI", "SC", "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY",
    "AS", "GU", "MP", "PR", "VI", "AP", "AE", "AA",
}
STATE_NUMERIC_CODES = {
    "01", "02", "04", "05", "06", "08", "09", "10", "11", "12", "13", "15", "16", "17", "18", "19", "20",
    "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31", "32", "33", "34", "35", "36", "37",
    "38", "39", "40", "41", "42", "44", "45", "46", "47", "48", "49", "50", "51", "53", "54", "55", "56",
}
COUNTRY_CODES = set("""
AA AC AE AF AG AJ AL AM AN AO AR AS AT AU AV AX AY BA BB BC BD BE BF BG BH BK BL BM BN BO BP BQ BR BS
BT BU BV BX BY CA CB CD CE CF CG CH CI CJ CK CM CN CO CR CS CT CU CV CW CY DA DJ DO DQ DR DX EC EG EI
EK EN ER ES ET EU EZ FG FI FJ FK FM FO FP FQ FR FS GA GB GH GI GJ GK GL GM GO GP GR GT GV GY GZ HA HK
HM HO HQ HR HU IC ID IM IN IO IP IR IS IT IV IZ JA JE JM JN JO JQ JU KE KG KN KQ KR KS KT KU KV KZ LA
LE LG LH LI LO LQ LS LT LU LY MA MB MC MD MF MG MH MI MJ MK ML MN MO MP MQ MR MT MU MV MX MY MZ NC NE
NF NG NH NI NL NM NN NO NP NR NS NU NZ OD PA PC PE PF PG PK PL PM PO PP PS PU QA RE RI RM RN RO RS RW
SA SB SC SE SF SG SH SI SL SM SN SO SP ST SU SV SW SX SY SZ TB TD TE TH TI TK TL TN TO TP TS TT TU TV
TW TX TZ UC UG UK UP UV UY UZ VC VE VI VM VT WA WE WF WI WQ WS WZ YM ZA ZI
""".split())

Field = namedtuple("Field", "name start end kind required allowed rule address source")
Field.__new__.__defaults__ = (False, None, None, None, None)


def F(name, start, end, kind, required=False, allowed=None, rule=None, address=None, source=None):
    return Field(name, start, end, kind, required, allowed, rule, address, source)


def blank(start, end):
    return F("Blank", start, end, "blank")


def ident(record_id):
    return F("Record Identifier", 1, 2, "fixed", True, {record_id})


def money(name, start, end, source=None, rule=None):
    return F(name, start, end, "money", True, source=source, rule=rule)


def address_block(prefix, address, start, *, delivery_required, city_required, state_zip_required,
                  foreign_required=False, gap_after_zip=5):
    loc = start
    delivery = loc + 22
    city = delivery + 22
    state = city + 22
    zip_code = state + 2
    zip_ext = zip_code + 5
    gap = zip_ext + 4
    foreign_state = gap + gap_after_zip
    foreign_postal = foreign_state + 23
    country = foreign_postal + 15
    domestic = "domestic" if state_zip_required else False
    foreign = "foreign" if foreign_required else False
    return [
        F(f"{prefix} Location Address", loc, delivery - 1, "text", address=address),
        F(f"{prefix} Delivery Address", delivery, city - 1, "text", delivery_required, address=address),
        F(f"{prefix} City", city, state - 1, "text", city_required, address=address),
        F(f"{prefix} State Abbreviation", state, zip_code - 1, "text", domestic, rule="state", address=address),
        F(f"{prefix} ZIP Code", zip_code, zip_ext - 1, "digits_full", domestic, rule="zip", address=address),
        F(f"{prefix} ZIP Code Extension", zip_ext, gap - 1, "digits_full", address=address),
        blank(gap, foreign_state - 1),
        F(f"{prefix} Foreign State/Province", foreign_state, foreign_postal - 1, "text", foreign, address=address),
        F(f"{prefix} Foreign Postal Code", foreign_postal, country - 1, "text", foreign, address=address),
        F(f"{prefix} Country Code", country, country + 1, "text", rule="country", address=address),
    ]


# Pub 42-007 TY2026 section 4.5 RA (Submitter) Record.
RA_FIELDS = [
    ident("RA"),
    F("Submitter's EIN", 3, 11, "digits_full", True, rule="submitter_ein"),
    F("User Identification (User ID)", 12, 19, "text", True, rule="user_id"),
    F("Software Vendor Code", 20, 23, "digits_full"),
    blank(24, 28),
    F("Resub Indicator", 29, 29, "code", True, {"0", "1"}),
    F("Resub Wage File Identifier (WFID)", 30, 35, "text"),
    F("Software Code", 36, 37, "code", True, {"98", "99"}),
    F("Company Name", 38, 94, "text"),
    *address_block("Company", "company", 95, delivery_required=False, city_required=False,
                   state_zip_required=False),
    F("Submitter Name", 217, 273, "text", True),
    *address_block("Submitter", "submitter", 274, delivery_required=True, city_required=True,
                   state_zip_required=True, foreign_required=True),
    F("Contact Name", 396, 422, "text", True),
    F("Contact Phone Number", 423, 437, "digits", True),
    F("Contact Phone Extension", 438, 442, "digits"),
    blank(443, 445),
    F("Contact E-Mail/Internet", 446, 485, "email", True),
    blank(486, 488),
    F("Contact Fax", 489, 498, "digits"),
    blank(499, 499),
    F("Preparer Code", 500, 500, "code", True, {"A", "L", "S", "P", "O"}),
    blank(501, 512),
]

# Pub 42-007 TY2026 section 4.6 RE (Employer) Record; codes from section 2.1, 2.5-2.8.
RE_FIELDS = [
    ident("RE"),
    F("Tax Year", 3, 6, "digits_full", True, rule="tax_year"),
    F("Agent Indicator Code", 7, 7, "code", False, {" ", "1", "2", "3"}),
    F("Employer/Agent EIN", 8, 16, "digits_full", True, rule="employer_ein"),
    F("Agent for EIN", 17, 25, "digits_full", rule="employer_ein"),
    F("Terminating Business Indicator", 26, 26, "code", True, {"0", "1"}),
    F("Establishment Number", 27, 30, "free"),
    F("Other EIN", 31, 39, "digits_full", rule="employer_ein"),
    F("Employer Name", 40, 96, "text", True),
    F("Employer Location Address", 97, 118, "text", address="employer"),
    F("Employer Delivery Address", 119, 140, "text", True, address="employer"),
    F("Employer City", 141, 162, "text", True, address="employer"),
    F("Employer State Abbreviation", 163, 164, "text", "domestic", rule="state", address="employer"),
    F("Employer ZIP Code", 165, 169, "digits_full", "domestic", rule="zip", address="employer"),
    F("Employer ZIP Code Extension", 170, 173, "digits_full", address="employer"),
    F("Kind of Employer", 174, 174, "code", False, {" ", "F", "S", "T", "Y", "N"}),
    blank(175, 178),
    F("Employer Foreign State/Province", 179, 201, "text", address="employer"),
    F("Employer Foreign Postal Code", 202, 216, "text", address="employer"),
    F("Employer Country Code", 217, 218, "text", rule="country", address="employer"),
    F("Employment Code", 219, 219, "code", True, {"A", "H", "M", "Q", "X", "F", "R"}),
    F("Tax Jurisdiction Code", 220, 220, "code", False, {" ", "V", "G", "S", "N", "P"}),
    F("Third-Party Sick Pay Indicator", 221, 221, "code", True, {"0", "1"}),
    F("Employer Contact Name", 222, 248, "text", True),
    F("Employer Contact Phone Number", 249, 263, "digits", True),
    F("Employer Contact Phone Extension", 264, 268, "digits"),
    F("Employer Contact Fax Number", 269, 278, "digits"),
    F("Employer Contact E-Mail/Internet", 279, 318, "email", True),
    F("Employer Signature PIN", 319, 328, "free", rule="signature_pin"),
    blank(329, 512),
]

# Pub 42-007 TY2026 section 4.7 RW (Employee) Record; SSN rules from section 4.2.
RW_FIELDS = [
    ident("RW"),
    F("Social Security Number (SSN)", 3, 11, "digits_full", True, rule="ssn"),
    F("Employee First Name", 12, 26, "text", True, rule="name"),
    F("Employee Middle Name or Initial", 27, 41, "text", rule="name"),
    F("Employee Last Name", 42, 61, "text", True, rule="name"),
    F("Suffix", 62, 65, "text", rule="suffix"),
    *address_block("Employee", "employee", 66, delivery_required=False, city_required=False,
                   state_zip_required=False),
    money("Wages, Tips and Other Compensation", 188, 198),
    money("Federal Income Tax Withheld", 199, 209),
    money("Social Security Wages", 210, 220),
    money("Social Security Tax Withheld", 221, 231),
    money("Medicare Wages and Tips", 232, 242),
    money("Medicare Tax Withheld", 243, 253),
    money("Social Security Tips", 254, 264),
    blank(265, 275),
    money("Dependent Care Benefits", 276, 286),
    money("401(k) Deferrals (Code D)", 287, 297),
    money("403(b) Deferrals (Code E)", 298, 308),
    money("408(k)(6) Deferrals (Code F)", 309, 319),
    money("457(b) Deferrals (Code G)", 320, 330),
    money("501(c)(18)(D) Deferrals (Code H)", 331, 341),
    blank(342, 352),
    money("Nonqualified Plan Section 457", 353, 363),
    money("Employer HSA Contributions (Code W)", 364, 374),
    money("Nonqualified Plan Not Section 457", 375, 385),
    money("Nontaxable Combat Pay (Code Q)", 386, 396),
    blank(397, 407),
    money("Group Term Life Over $50,000 (Code C)", 408, 418),
    money("Nonstatutory Stock Options (Code V)", 419, 429),
    money("409A Deferrals (Code Y)", 430, 440),
    money("Roth 401(k) (Code AA)", 441, 451),
    money("Roth 403(b) (Code BB)", 452, 462),
    money("Employer-Sponsored Health Coverage (Code DD)", 463, 473),
    money("QSEHRA Benefits (Code FF)", 474, 484),
    blank(485, 485),
    F("Statutory Employee Indicator", 486, 486, "code", True, {"0", "1"}),
    blank(487, 487),
    F("Retirement Plan Indicator", 488, 488, "code", True, {"0", "1"}),
    F("Third-Party Sick Pay Indicator", 489, 489, "code", True, {"0", "1"}),
    F("Primary Treasury Tipped Occupation Code", 490, 492, "digits_full"),
    F("Secondary Treasury Tipped Occupation Code", 493, 495, "digits_full"),
    blank(496, 512),
]

# Pub 42-007 TY2026 section 4.8 RO (Employee Optional) Record.
RO_FIELDS = [
    ident("RO"),
    blank(3, 11),
    money("Allocated Tips", 12, 22),
    money("Uncollected Employee Tax on Tips (Codes A and B)", 23, 33, rule="retired_after_2025"),
    money("Medical Savings Account (Code R)", 34, 44),
    money("Simple Retirement Account (Code S)", 45, 55),
    money("Qualified Adoption Expenses (Code T)", 56, 66),
    money("Uncollected SS/RRTA Tax on Group Term Life (Code M)", 67, 77),
    money("Uncollected Medicare Tax on Group Term Life (Code N)", 78, 88),
    money("Failed 409A Income (Code Z)", 89, 99),
    blank(100, 110),
    money("Roth 457(b) (Code EE)", 111, 121),
    money("83(i) Equity Grant Income (Code GG)", 122, 132),
    money("83(i) Aggregate Deferrals (Code HH)", 133, 143),
    money("Medicaid Waiver Payments (Code II)", 144, 154),
    money("Uncollected SS/RRTA Tax on Tips (Code A)", 155, 165),
    money("Uncollected Medicare Tax on Tips (Code B)", 166, 176),
    money("Nontaxable Sick Pay (Code J)", 177, 187),
    money("Golden Parachute Excise Tax (Code K)", 188, 198),
    money("Substantiated Business Expense Reimbursements (Code L)", 199, 209),
    money("Armed Services Moving Expense Reimbursements (Code P)", 210, 220),
    money("Trump Account Employer Contributions (Code TA)", 221, 231),
    money("Cash Tips Reported to the Employer (Code TP)", 232, 242),
    money("Qualified Overtime Compensation (Code TT)", 243, 253),
    blank(254, 274),
    money("Wages Subject to Puerto Rico Tax", 275, 285),
    money("Commissions Subject to Puerto Rico Tax", 286, 296),
    money("Allowances Subject to Puerto Rico Tax", 297, 307),
    money("Tips Subject to Puerto Rico Tax", 308, 318),
    money("Total Subject to Puerto Rico Tax", 319, 329),
    money("Puerto Rico Tax Withheld", 330, 340),
    money("Retirement Fund Annual Contributions", 341, 351),
    blank(352, 362),
    money("Wages Subject to VI/GU/AS/MP Income Tax", 363, 373),
    money("VI/GU/AS/MP Income Tax Withheld", 374, 384),
    blank(385, 512),
]

# Pub 42-007 TY2026 section 4.9 RS (State) Record; state codes from Appendix F.
RS_FIELDS = [
    ident("RS"),
    F("State Code", 3, 4, "digits_full", rule="state_numeric"),
    F("Taxing Entity Code", 5, 9, "free"),
    F("Social Security Number (SSN)", 10, 18, "digits_full"),
    F("Employee First Name", 19, 33, "text"),
    F("Employee Middle Name or Initial", 34, 48, "text"),
    F("Employee Last Name", 49, 68, "text"),
    F("Suffix", 69, 72, "text"),
    *address_block("Employee", "state-employee", 73, delivery_required=False, city_required=False,
                   state_zip_required=False),
    F("Optional Code", 195, 196, "free"),
    F("Reporting Period", 197, 202, "free"),
    F("State Quarterly UI Total Wages", 203, 213, "state_money"),
    F("State Quarterly UI Total Taxable Wages", 214, 224, "state_money"),
    F("Number of Weeks Worked", 225, 226, "free"),
    F("Date First Employed", 227, 234, "free", rule="date"),
    F("Date of Separation", 235, 242, "free", rule="date"),
    blank(243, 247),
    F("State Employer Account Number", 248, 267, "free"),
    blank(268, 273),
    F("Income Tax State Code", 274, 275, "digits_full", rule="state_numeric"),
    F("State Taxable Wages", 276, 286, "state_money"),
    F("State Income Tax Withheld", 287, 297, "state_money"),
    F("Other State Data", 298, 307, "free"),
    F("Tax Type Code", 308, 308, "code", False, {" ", "C", "D", "E", "F"}),
    F("Local Taxable Wages", 309, 319, "free"),
    F("Local Income Tax Withheld", 320, 330, "free"),
    F("State Control Number", 331, 337, "free"),
    F("Supplemental Data 1", 338, 412, "free"),
    F("Supplemental Data 2", 413, 487, "free"),
    blank(488, 512),
]


def total(name, start, end, source):
    return money(name, start, end, source=source)


# Pub 42-007 TY2026 section 4.10 RT (Total) Record.
RT_FIELDS = [
    ident("RT"),
    F("Total Number of RW Records", 3, 9, "count", True),
    total("Total Wages, Tips and Other Compensation", 10, 24, "Wages, Tips and Other Compensation"),
    total("Total Federal Income Tax Withheld", 25, 39, "Federal Income Tax Withheld"),
    total("Total Social Security Wages", 40, 54, "Social Security Wages"),
    total("Total Social Security Tax Withheld", 55, 69, "Social Security Tax Withheld"),
    total("Total Medicare Wages and Tips", 70, 84, "Medicare Wages and Tips"),
    total("Total Medicare Tax Withheld", 85, 99, "Medicare Tax Withheld"),
    total("Total Social Security Tips", 100, 114, "Social Security Tips"),
    blank(115, 129),
    total("Total Dependent Care Benefits", 130, 144, "Dependent Care Benefits"),
    total("Total 401(k) Deferrals (Code D)", 145, 159, "401(k) Deferrals (Code D)"),
    total("Total 403(b) Deferrals (Code E)", 160, 174, "403(b) Deferrals (Code E)"),
    total("Total 408(k)(6) Deferrals (Code F)", 175, 189, "408(k)(6) Deferrals (Code F)"),
    total("Total 457(b) Deferrals (Code G)", 190, 204, "457(b) Deferrals (Code G)"),
    total("Total 501(c)(18)(D) Deferrals (Code H)", 205, 219, "501(c)(18)(D) Deferrals (Code H)"),
    blank(220, 234),
    total("Total Nonqualified Plan Section 457", 235, 249, "Nonqualified Plan Section 457"),
    total("Total Employer HSA Contributions (Code W)", 250, 264, "Employer HSA Contributions (Code W)"),
    total("Total Nonqualified Plan Not Section 457", 265, 279, "Nonqualified Plan Not Section 457"),
    total("Total Nontaxable Combat Pay (Code Q)", 280, 294, "Nontaxable Combat Pay (Code Q)"),
    total("Total Employer-Sponsored Health Coverage (Code DD)", 295, 309,
          "Employer-Sponsored Health Coverage (Code DD)"),
    total("Total Group Term Life Over $50,000 (Code C)", 310, 324, "Group Term Life Over $50,000 (Code C)"),
    money("Total Income Tax Withheld by Payer of Third-Party Sick Pay", 325, 339),
    total("Total Nonstatutory Stock Options (Code V)", 340, 354, "Nonstatutory Stock Options (Code V)"),
    total("Total 409A Deferrals (Code Y)", 355, 369, "409A Deferrals (Code Y)"),
    total("Total Roth 401(k) (Code AA)", 370, 384, "Roth 401(k) (Code AA)"),
    total("Total Roth 403(b) (Code BB)", 385, 399, "Roth 403(b) (Code BB)"),
    total("Total QSEHRA Benefits (Code FF)", 400, 414, "QSEHRA Benefits (Code FF)"),
    blank(415, 512),
]

# Pub 42-007 TY2026 section 4.11 RU (Total Optional) Record.
RU_FIELDS = [
    ident("RU"),
    F("Total Number of RO Records", 3, 9, "count", True),
    total("Total Allocated Tips", 10, 24, "Allocated Tips"),
    F("Total Uncollected Employee Tax on Tips (Codes A and B)", 25, 39, "money", True,
      rule="retired_after_2025", source="Uncollected Employee Tax on Tips (Codes A and B)"),
    total("Total Medical Savings Account (Code R)", 40, 54, "Medical Savings Account (Code R)"),
    total("Total Simple Retirement Account (Code S)", 55, 69, "Simple Retirement Account (Code S)"),
    total("Total Qualified Adoption Expenses (Code T)", 70, 84, "Qualified Adoption Expenses (Code T)"),
    total("Total Uncollected SS/RRTA Tax on Group Term Life (Code M)", 85, 99,
          "Uncollected SS/RRTA Tax on Group Term Life (Code M)"),
    total("Total Uncollected Medicare Tax on Group Term Life (Code N)", 100, 114,
          "Uncollected Medicare Tax on Group Term Life (Code N)"),
    total("Total Failed 409A Income (Code Z)", 115, 129, "Failed 409A Income (Code Z)"),
    blank(130, 144),
    total("Total Roth 457(b) (Code EE)", 145, 159, "Roth 457(b) (Code EE)"),
    total("Total 83(i) Equity Grant Income (Code GG)", 160, 174, "83(i) Equity Grant Income (Code GG)"),
    total("Total 83(i) Aggregate Deferrals (Code HH)", 175, 189, "83(i) Aggregate Deferrals (Code HH)"),
    total("Total Medicaid Waiver Payments (Code II)", 190, 204, "Medicaid Waiver Payments (Code II)"),
    total("Total Uncollected SS/RRTA Tax on Tips (Code A)", 205, 219, "Uncollected SS/RRTA Tax on Tips (Code A)"),
    total("Total Uncollected Medicare Tax on Tips (Code B)", 220, 234, "Uncollected Medicare Tax on Tips (Code B)"),
    total("Total Nontaxable Sick Pay (Code J)", 235, 249, "Nontaxable Sick Pay (Code J)"),
    total("Total Golden Parachute Excise Tax (Code K)", 250, 264, "Golden Parachute Excise Tax (Code K)"),
    total("Total Substantiated Business Expense Reimbursements (Code L)", 265, 279,
          "Substantiated Business Expense Reimbursements (Code L)"),
    total("Total Armed Services Moving Expense Reimbursements (Code P)", 280, 294,
          "Armed Services Moving Expense Reimbursements (Code P)"),
    total("Total Trump Account Employer Contributions (Code TA)", 295, 309,
          "Trump Account Employer Contributions (Code TA)"),
    total("Total Cash Tips Reported to the Employer (Code TP)", 310, 324,
          "Cash Tips Reported to the Employer (Code TP)"),
    total("Total Qualified Overtime Compensation (Code TT)", 325, 339, "Qualified Overtime Compensation (Code TT)"),
    blank(340, 354),
    total("Total Wages Subject to Puerto Rico Tax", 355, 369, "Wages Subject to Puerto Rico Tax"),
    total("Total Commissions Subject to Puerto Rico Tax", 370, 384, "Commissions Subject to Puerto Rico Tax"),
    total("Total Allowances Subject to Puerto Rico Tax", 385, 399, "Allowances Subject to Puerto Rico Tax"),
    total("Total Tips Subject to Puerto Rico Tax", 400, 414, "Tips Subject to Puerto Rico Tax"),
    total("Total Subject to Puerto Rico Tax", 415, 429, "Total Subject to Puerto Rico Tax"),
    total("Total Puerto Rico Tax Withheld", 430, 444, "Puerto Rico Tax Withheld"),
    total("Total Retirement Fund Annual Contributions", 445, 459, "Retirement Fund Annual Contributions"),
    total("Total Wages Subject to VI/GU/AS/MP Income Tax", 460, 474, "Wages Subject to VI/GU/AS/MP Income Tax"),
    total("Total VI/GU/AS/MP Income Tax Withheld", 475, 489, "VI/GU/AS/MP Income Tax Withheld"),
    blank(490, 512),
]

# Pub 42-007 TY2026 section 4.12 RV (State Total) Record.
RV_FIELDS = [ident("RV"), F("Supplemental Data", 3, 512, "free")]

# Pub 42-007 TY2026 section 4.13 RF (Final) Record.
RF_FIELDS = [ident("RF"), blank(3, 7), F("Number of RW Records", 8, 16, "count", True), blank(17, 512)]

LAYOUTS = {
    "RA": RA_FIELDS, "RE": RE_FIELDS, "RW": RW_FIELDS, "RO": RO_FIELDS, "RS": RS_FIELDS,
    "RT": RT_FIELDS, "RU": RU_FIELDS, "RV": RV_FIELDS, "RF": RF_FIELDS,
}

# Pub 42-007 TY2026 sections 3.2.1-3.2.7 and Appendix C: which record may follow which.
ALLOWED_NEXT = {
    None: {"RA"},
    "RA": {"RE"},
    "RE": {"RW"},
    "RW": {"RW", "RO", "RS", "RT"},
    "RO": {"RW", "RS", "RT"},
    "RS": {"RS", "RW", "RT"},
    "RT": {"RU", "RV", "RE", "RF"},
    "RU": {"RV", "RE", "RF"},
    "RV": {"RE", "RF"},
    "RF": set(),
}


def assert_layouts_cover_every_position():
    for record_id, fields in LAYOUTS.items():
        expected_start = 1
        for field in fields:
            if field.start != expected_start or field.end < field.start:
                raise AssertionError(f"{record_id} layout gap or overlap at {field.name} {field.start}-{field.end}")
            expected_start = field.end + 1
        if expected_start != RECORD_LENGTH + 1:
            raise AssertionError(f"{record_id} layout ends at {expected_start - 1}, not {RECORD_LENGTH}")
        names = [f.name for f in fields if f.kind != "blank"]
        if len(names) != len(set(names)):
            raise AssertionError(f"{record_id} layout has duplicate field names")


assert_layouts_cover_every_position()

Finding = namedtuple("Finding", "level number record_id positions message")


class Record:
    def __init__(self, number, text, fields):
        self.number = number
        self.text = text
        self.record_id = text[:2]
        self.by_name = {f.name: f for f in fields}

    def raw(self, field):
        return self.text[field.start - 1:field.end]

    def value(self, name):
        return self.raw(self.by_name[name])

    def amount(self, name):
        raw = self.value(name)
        return int(raw) if raw.isdigit() else 0


def positions(start, end):
    return str(start) if start == end else f"{start}-{end}"


def dollars(cents):
    return f"${cents / 100:,.2f}"


class Checker:
    def __init__(self):
        self.findings = []

    def report(self, level, record, start, end, message):
        number = record.number if record else 0
        record_id = record.record_id if record else "--"
        self.findings.append(Finding(level, number, record_id, positions(start, end) if start else "-", message))

    def error(self, record, field_or_start, end_or_message, message=None):
        self._emit("ERROR", record, field_or_start, end_or_message, message)

    def warn(self, record, field_or_start, end_or_message, message=None):
        self._emit("WARN", record, field_or_start, end_or_message, message)

    def _emit(self, level, record, field_or_start, end_or_message, message):
        if isinstance(field_or_start, Field):
            self.report(level, record, field_or_start.start, field_or_start.end,
                        f"{field_or_start.name}: {end_or_message}")
        else:
            self.report(level, record, field_or_start, end_or_message, message)


def split_records(data, checker):
    if data.startswith(RECORD_DELIMITER):
        checker.report("ERROR", None, 0, 0, "file starts with a record delimiter; none may precede the first record")
    chunks = data.split(RECORD_DELIMITER)
    if chunks and chunks[-1] == b"":
        chunks.pop()
    elif chunks:
        checker.report("ERROR", None, 0, 0, "last record is not terminated by CR/LF")
    return chunks


def check_bytes(record_number, chunk, checker):
    """Return the record text, or None when the record cannot be parsed by position."""
    stub = Record(record_number, chunk[:2].decode("latin-1").ljust(2), [])
    stray_delimiters = [i + 1 for i, b in enumerate(chunk) if b in (0x0A, 0x0D)]
    if stray_delimiters:
        at = stray_delimiters[0]
        checker.error(stub, at, at, "bare CR or LF inside a record; each record ends with CR/LF after position 512")
    non_printable = [i + 1 for i, b in enumerate(chunk) if (b < 0x20 or b > 0x7E) and b not in (0x0A, 0x0D)]
    if non_printable:
        at = non_printable[0]
        checker.error(stub, at, at, f"{len(non_printable)} byte(s) outside printable ASCII-1 (first at position {at})")
    if len(chunk) != RECORD_LENGTH:
        checker.error(stub, 1, max(len(chunk), 1), f"record length is {len(chunk)} bytes, must be {RECORD_LENGTH}")
        return None
    return chunk.decode("latin-1")


def luhn_check_digit(digits):
    total_sum = 0
    for index, char in enumerate(reversed(digits)):
        digit = int(char)
        if index % 2 == 0:
            digit *= 2
            if digit > 9:
                digit -= 9
        total_sum += digit
    return (10 - total_sum % 10) % 10


EMAIL_LOCAL_CHARS = set("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789~!#$%^&*_+{}|.?'-=/`")
EMAIL_DOMAIN_CHARS = set("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-.")


def email_problem(raw):
    address = raw.rstrip(" ")
    if address != address.lstrip(" "):
        return "must be left justified"
    if " " in address:
        return "must not contain spaces"
    if address.count("@") != 1:
        return "must contain exactly one @"
    if address[0] in ".@" or address[-1] in ".@":
        return "must not begin or end with a period or @"
    if ".." in address:
        return "must not contain consecutive periods"
    if ".@" in address or "@." in address:
        return "must not have a period next to the @"
    local, domain = address.split("@")
    if not set(local) <= EMAIL_LOCAL_CHARS:
        return "local part has a character outside the allowed set"
    if not set(domain) <= EMAIL_DOMAIN_CHARS:
        return "domain may contain only letters, digits, hyphens and periods"
    if domain.startswith("-") or "-." in domain or ".-" in domain:
        return "hyphen must not follow the @ or touch a period"
    if "." not in domain:
        return "domain has no top-level domain"
    top_level = domain.rsplit(".", 1)[1]
    if len(top_level) < 2 or not top_level.isalpha():
        return f"top-level domain '{top_level}' is not a valid IANA form"
    return None


def check_field(record, field, checker):
    raw = record.raw(field)
    stripped = raw.strip(" ")
    is_blank = stripped == ""
    kind = field.kind

    if kind == "blank":
        if not is_blank:
            checker.error(record, field, "reserved positions must be blank")
        return
    if kind == "fixed":
        if raw not in field.allowed:
            checker.error(record, field, f"must be {sorted(field.allowed)[0]!r}")
        return
    if kind == "code":
        if raw not in field.allowed:
            shown = ", ".join(repr(v) for v in sorted(field.allowed))
            checker.error(record, field, f"{raw!r} is not one of {shown}")
        return
    if kind in ("money", "count"):
        if not raw.isdigit():
            what = "blank; fill with zeros" if is_blank else f"{raw!r} must be digits only, right justified and zero filled"
            checker.error(record, field, what)
        return
    if kind == "state_money":
        if not is_blank and not raw.isdigit():
            checker.error(record, field, f"{raw!r} must be right justified and zero filled")
        return

    if field.required is True and is_blank:
        checker.error(record, field, "required field is blank")
        return
    if is_blank:
        return
    if kind in ("text", "digits", "digits_full", "email") and raw[0] == " ":
        checker.error(record, field, "must be left justified")
    if kind == "text" and any("a" <= c <= "z" for c in raw):
        checker.error(record, field, "lower-case letters are not allowed; use upper case")
    if kind == "digits":
        if not re.fullmatch(r"[0-9]+ *", raw):
            checker.error(record, field, f"{raw!r} must be numeric only, left justified")
    if kind == "digits_full" and not raw.isdigit():
        checker.error(record, field, f"{raw!r} must be {len(raw)} digits")
    if kind == "email":
        problem = email_problem(raw)
        if problem:
            checker.error(record, field, f"{stripped!r} {problem}")
    check_rule(record, field, raw, checker)


def check_rule(record, field, raw, checker):
    rule = field.rule
    if rule in ("submitter_ein", "employer_ein") and raw.isdigit():
        if rule == "submitter_ein" and raw == "000000000":
            return
        bad = SUBMITTER_EIN_BAD_PREFIXES if rule == "submitter_ein" else EMPLOYER_EIN_BAD_PREFIXES
        if raw[:2] in bad:
            checker.error(record, field, f"EIN may not begin with {raw[:2]}")
    elif rule == "ssn" and raw.isdigit():
        if raw == "000000000":
            checker.warn(record, field, "no SSN reported (zeros); file an EFW2C when the SSN is known")
        elif raw.startswith("666") or raw.startswith("9"):
            checker.error(record, field, f"SSN may not begin with 666 or 9 ({raw[:3]})")
        elif raw[:3] == "000" or raw[3:5] == "00" or raw[5:] == "0000":
            checker.warn(record, field, "SSN has an all-zero area, group or serial; SSA never assigns these")
    elif rule == "tax_year" and raw.isdigit() and int(raw) != TAX_YEAR:
        checker.error(record, field, f"tax year {raw}; this checker encodes the TY{TAX_YEAR} layout only")
    elif rule == "state" and raw.strip() and raw not in STATE_ABBREVIATIONS:
        checker.error(record, field, f"{raw!r} is not an Appendix F postal abbreviation")
    elif rule == "state_numeric" and raw.strip() and raw not in STATE_NUMERIC_CODES:
        checker.error(record, field, f"{raw!r} is not an Appendix F numeric state code")
    elif rule == "country" and raw.strip() and raw not in COUNTRY_CODES:
        checker.error(record, field, f"{raw!r} is not an Appendix G country code")
    elif rule == "name":
        if not re.fullmatch(r"[A-Z' -]*", raw):
            checker.warn(record, field, f"{raw.strip()!r} has characters other than A-Z, blank, hyphen, apostrophe")
    elif rule == "suffix":
        if not re.fullmatch(r"[A-Z]+ *", raw):
            checker.warn(record, field, f"{raw.strip()!r} must be alphabetic (for example SR, JR)")
    elif rule == "user_id":
        if not re.fullmatch(r"[A-Z0-9]{8}", raw):
            checker.warn(record, field, f"{raw!r} is not an eight-character BSO User ID")
    elif rule == "signature_pin":
        match = re.fullmatch(r"S-([0-9]{6})-([0-9])", raw)
        if not match:
            checker.error(record, field, f"{raw!r} must be S-XXXXXX-Y or blank")
        elif luhn_check_digit(match.group(1)) != int(match.group(2)):
            checker.error(record, field, f"{raw!r} check digit fails the Luhn algorithm")
    elif rule == "date" and not re.fullmatch(r"(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[0-9]{4}", raw):
        checker.warn(record, field, f"{raw!r} is not MMDDYYYY")


def check_addresses(record, fields, checker):
    groups = {}
    for field in fields:
        if field.address:
            groups.setdefault(field.address, []).append(field)
    for group in groups.values():
        country = next(f for f in group if f.rule == "country")
        foreign = record.raw(country).strip() != ""
        for field in group:
            filled = record.raw(field).strip() != ""
            if foreign and field.rule in ("state", "zip") and filled:
                checker.error(record, field, "must be blank for a foreign address")
            if foreign and field.name.endswith("ZIP Code Extension") and filled:
                checker.error(record, field, "must be blank for a foreign address")
            if not foreign and field.required == "domestic" and not filled:
                checker.error(record, field, "required for a U.S. address")
            if foreign and field.required == "foreign" and not filled:
                checker.error(record, field, "required for a foreign address")


def check_record_fields(record, fields, checker):
    for field in fields:
        check_field(record, field, checker)
    check_addresses(record, fields, checker)
    for field in fields:
        if field.rule == "retired_after_2025" and record.raw(field).isdigit() and int(record.raw(field)) > 0:
            checker.error(record, field, f"valid only through tax year 2025; report Codes A and B in their own fields")


def check_ra(record, checker):
    f = record.by_name
    resub, wfid = record.value("Resub Indicator"), record.value("Resub Wage File Identifier (WFID)").strip()
    if resub == "1" and not wfid:
        checker.error(record, f["Resub Wage File Identifier (WFID)"], "required when Resub Indicator is 1")
    if resub == "0" and wfid:
        checker.error(record, f["Resub Wage File Identifier (WFID)"], "must be blank when Resub Indicator is 0")
    vendor = record.value("Software Vendor Code").strip()
    if record.value("Software Code") == "99" and not vendor:
        checker.error(record, f["Software Vendor Code"], "required when Software Code is 99")
    if record.value("Software Code") == "98" and vendor:
        checker.error(record, f["Software Vendor Code"], "must be blank when Software Code is 98")


def check_re(record, checker):
    f = record.by_name
    agent = record.value("Agent Indicator Code")
    agent_for = record.value("Agent for EIN").strip()
    if agent == "1" and not agent_for:
        checker.error(record, f["Agent for EIN"], "required when Agent Indicator Code is 1")
    if agent == " " and agent_for:
        checker.error(record, f["Agent for EIN"], "must be blank when there is no Agent Indicator Code")
    jurisdiction = record.value("Tax Jurisdiction Code")
    kind = record.value("Kind of Employer")
    if jurisdiction == "P" and kind != " ":
        checker.warn(record, f["Kind of Employer"], "leave blank when Tax Jurisdiction Code is P")
    if jurisdiction != "P" and kind == " ":
        checker.error(record, f["Kind of Employer"], "required field is blank")
    if jurisdiction != " " and record.value("Employment Code") == "X":
        checker.error(record, f["Employment Code"], "railroad reporting does not apply to Puerto Rico or territorial employers")


class Employee:
    def __init__(self, rw):
        self.rw = rw
        self.ro = None
        self.rs_count = 0


def check_employee(employee, employer, checker):
    rw, ro = employee.rw, employee.ro
    f = rw.by_name
    code = employer.value("Employment Code")
    jurisdiction = employer.value("Tax Jurisdiction Code")
    ss_wages = rw.amount("Social Security Wages")
    ss_tips = rw.amount("Social Security Tips")
    ss_tax = rw.amount("Social Security Tax Withheld")
    medicare_wages = rw.amount("Medicare Wages and Tips")
    medicare_tax = rw.amount("Medicare Tax Withheld")
    ss_base = ss_wages + ss_tips

    if code in ("Q", "X"):
        for name in ("Social Security Wages", "Social Security Tips", "Social Security Tax Withheld"):
            if rw.amount(name):
                checker.error(rw, f[name], f"must be zero when Employment Code is {code}")
    if code == "X":
        for name in ("Medicare Wages and Tips", "Medicare Tax Withheld"):
            if rw.amount(name):
                checker.error(rw, f[name], "must be zero when Employment Code is X (Railroad)")
    if code == "H":
        if 0 < ss_base < HOUSEHOLD_COVERAGE_MINIMUM_CENTS:
            checker.error(rw, 210, 264, f"Household: Social Security wages + tips {dollars(ss_base)} is nonzero and below the {dollars(HOUSEHOLD_COVERAGE_MINIMUM_CENTS)} coverage minimum; report zeros")
        if 0 < medicare_wages < HOUSEHOLD_COVERAGE_MINIMUM_CENTS:
            checker.error(rw, f["Medicare Wages and Tips"], f"Household: {dollars(medicare_wages)} is nonzero and below the {dollars(HOUSEHOLD_COVERAGE_MINIMUM_CENTS)} coverage minimum; report zeros")

    if ss_base > SOCIAL_SECURITY_WAGE_BASE_CENTS:
        checker.error(rw, 210, 264, f"Social Security wages + tips {dollars(ss_base)} exceed the {TAX_YEAR} wage base {dollars(SOCIAL_SECURITY_WAGE_BASE_CENTS)}")
    if ss_tax > SOCIAL_SECURITY_MAX_TAX_CENTS:
        checker.error(rw, f["Social Security Tax Withheld"], f"{dollars(ss_tax)} exceeds the {TAX_YEAR} maximum {dollars(SOCIAL_SECURITY_MAX_TAX_CENTS)}")
    if ss_tax > 0 and ss_base == 0 and code not in ("Q", "X"):
        checker.error(rw, f["Social Security Tax Withheld"], "is greater than zero while Social Security wages and tips are zero")
    if medicare_tax > 0 and medicare_wages == 0:
        checker.error(rw, f["Medicare Tax Withheld"], "is greater than zero while Medicare wages and tips are zero")
    if medicare_wages < ss_base:
        checker.error(rw, f["Medicare Wages and Tips"], f"{dollars(medicare_wages)} is less than Social Security wages + tips {dollars(ss_base)}")

    def uncollected(name):
        return ro.amount(name) if ro else 0

    ss_expected = round(min(ss_base, SOCIAL_SECURITY_WAGE_BASE_CENTS) * SOCIAL_SECURITY_RATE)
    ss_uncollected = uncollected("Uncollected SS/RRTA Tax on Tips (Code A)") + uncollected("Uncollected SS/RRTA Tax on Group Term Life (Code M)")
    if ss_base and not (ss_expected - ss_uncollected - TAX_ROUNDING_TOLERANCE_CENTS <= ss_tax <= ss_expected + TAX_ROUNDING_TOLERANCE_CENTS):
        checker.warn(rw, f["Social Security Tax Withheld"], f"{dollars(ss_tax)} differs from 6.2% of wages + tips ({dollars(ss_expected)}) by more than {dollars(TAX_ROUNDING_TOLERANCE_CENTS)} after uncollected Codes A/M")

    medicare_base_tax = round(medicare_wages * MEDICARE_RATE)
    additional = round(max(0, medicare_wages - ADDITIONAL_MEDICARE_THRESHOLD_CENTS) * ADDITIONAL_MEDICARE_RATE)
    medicare_uncollected = uncollected("Uncollected Medicare Tax on Tips (Code B)") + uncollected("Uncollected Medicare Tax on Group Term Life (Code N)")
    low = medicare_base_tax - medicare_uncollected - TAX_ROUNDING_TOLERANCE_CENTS
    high = medicare_base_tax + additional + TAX_ROUNDING_TOLERANCE_CENTS
    if medicare_wages and code != "X" and not (low <= medicare_tax <= high):
        expected = dollars(medicare_base_tax) if not additional else f"{dollars(medicare_base_tax)} to {dollars(medicare_base_tax + additional)} with the 0.9% Additional Medicare Tax"
        checker.warn(rw, f["Medicare Tax Withheld"], f"{dollars(medicare_tax)} is outside 1.45% of Medicare wages ({expected}) by more than {dollars(TAX_ROUNDING_TOLERANCE_CENTS)} after uncollected Codes B/N")

    if rw.value("Third-Party Sick Pay Indicator") == "1" and employer.value("Third-Party Sick Pay Indicator") != "1":
        checker.error(rw, f["Third-Party Sick Pay Indicator"], "is 1 but the RE Third-Party Sick Pay Indicator (position 221) is not 1")

    if jurisdiction != " ":
        for name in ("Wages, Tips and Other Compensation", "Federal Income Tax Withheld"):
            if rw.amount(name):
                checker.warn(rw, f[name], f"not processed for Tax Jurisdiction Code {jurisdiction}; report in the RO territorial fields")
    if ro:
        territorial = ("Wages Subject to VI/GU/AS/MP Income Tax", "VI/GU/AS/MP Income Tax Withheld")
        puerto_rico = ("Wages Subject to Puerto Rico Tax", "Commissions Subject to Puerto Rico Tax",
                       "Allowances Subject to Puerto Rico Tax", "Tips Subject to Puerto Rico Tax",
                       "Total Subject to Puerto Rico Tax", "Puerto Rico Tax Withheld", "Retirement Fund Annual Contributions")
        out_of_place = (territorial if jurisdiction not in ("V", "G", "S", "N") else ()) + (puerto_rico if jurisdiction != "P" else ())
        for name in out_of_place:
            if ro.amount(name):
                checker.warn(ro, ro.by_name[name], f"applies only to {'Puerto Rico' if name in puerto_rico else 'VI/GU/AS/MP'} employees; Tax Jurisdiction Code is {jurisdiction!r}")

    tips_reported = uncollected("Cash Tips Reported to the Employer (Code TP)")
    primary = rw.value("Primary Treasury Tipped Occupation Code").strip()
    secondary = rw.value("Secondary Treasury Tipped Occupation Code").strip()
    if tips_reported and not primary:
        checker.error(rw, f["Primary Treasury Tipped Occupation Code"], "required when RO Code TP (Cash Tips Reported to the Employer) is present")
    if secondary and not primary:
        checker.error(rw, f["Secondary Treasury Tipped Occupation Code"], "present without a Primary Treasury Tipped Occupation Code")
    if primary and not tips_reported:
        checker.warn(rw, f["Primary Treasury Tipped Occupation Code"], "present but no RO Code TP amount is reported")


def check_totals(total_record, detail_records, count_name, checker):
    f = total_record.by_name
    count = total_record.amount(count_name)
    if count != len(detail_records):
        checker.error(total_record, f[count_name], f"says {count}, counted {len(detail_records)}")
    for field in total_record.by_name.values():
        if not field.source:
            continue
        expected = sum(r.amount(field.source) for r in detail_records)
        actual = total_record.amount(field.name)
        if actual != expected:
            checker.error(total_record, field, f"{dollars(actual)} does not equal the sum {dollars(expected)}")


def check_employer_block(employer, employees, rt, ru, rv_seen, checker):
    for employee in employees:
        check_employee(employee, employer, checker)
    ros = [e.ro for e in employees if e.ro]
    if rt:
        check_totals(rt, [e.rw for e in employees], "Total Number of RW Records", checker)
        rt_ss = rt.amount("Total Social Security Wages") + rt.amount("Total Social Security Tips")
        if rt.amount("Total Medicare Wages and Tips") < rt_ss:
            checker.error(rt, rt.by_name["Total Medicare Wages and Tips"], "is less than total Social Security wages + tips")
    if ros and not ru:
        checker.error(rt or employer, 1, 2, "RU (Total Optional) Record is required because this employer has RO records")
    if ru:
        if not ros:
            checker.error(ru, 1, 2, "RU present but this employer has no RO records")
        check_totals(ru, ros, "Total Number of RO Records", checker)
    rs_total = sum(e.rs_count for e in employees)
    if rv_seen and not rs_total:
        checker.error(rv_seen, 1, 2, "RV present but this employer has no RS records")
    if rs_total and not rv_seen:
        checker.warn(rt or employer, 1, 2, "employer has RS records but no RV (State Total) Record")


def check_ro_not_empty(record, checker):
    if record.text[2:].replace("0", "").strip() == "":
        checker.error(record, 3, 512, "RO holds only blanks and zeros; do not write it")


def check_ru_not_empty(record, checker):
    if record.text[9:].replace("0", "").strip() == "":
        checker.error(record, 3, 512, "RU totals are all zero; do not write it")


def check_file(path):
    checker = Checker()
    with open(path, "rb") as handle:
        data = handle.read()
    chunks = split_records(data, checker)
    if not chunks:
        checker.report("ERROR", None, 0, 0, "file is empty")
        return checker.findings

    previous_id = None
    employer = None
    employees = []
    rt = ru = rv = None
    total_rw = 0
    re_count = 0
    rf = None

    def close_employer():
        if employer:
            check_employer_block(employer, employees, rt, ru, rv, checker)

    for number, chunk in enumerate(chunks, start=1):
        text = check_bytes(number, chunk, checker)
        record_id = chunk[:2].decode("latin-1")
        stub = Record(number, record_id.ljust(2), [])
        if record_id not in LAYOUTS:
            checker.error(stub, 1, 2, f"unknown record identifier {record_id!r}")
            continue
        if rf is not None:
            checker.error(stub, 1, 2, "data after the RF (Final) Record")
        elif record_id not in ALLOWED_NEXT[previous_id]:
            allowed = ", ".join(sorted(ALLOWED_NEXT[previous_id])) or "nothing"
            checker.error(stub, 1, 2, f"{record_id} cannot follow {previous_id or 'start of file'}; expected {allowed}")
        previous_id = record_id
        if text is None:
            continue

        record = Record(number, text, LAYOUTS[record_id])
        check_record_fields(record, LAYOUTS[record_id], checker)

        if record_id == "RA":
            check_ra(record, checker)
        elif record_id == "RE":
            close_employer()
            employer, employees, rt, ru, rv = record, [], None, None, None
            re_count += 1
            check_re(record, checker)
        elif record_id == "RW":
            employees.append(Employee(record))
            total_rw += 1
        elif record_id == "RO":
            check_ro_not_empty(record, checker)
            if employees and employees[-1].ro is None:
                employees[-1].ro = record
        elif record_id == "RS":
            if employees:
                employees[-1].rs_count += 1
        elif record_id == "RT":
            rt = record
        elif record_id == "RU":
            check_ru_not_empty(record, checker)
            ru = record
        elif record_id == "RV":
            rv = record
        elif record_id == "RF":
            rf = record

    close_employer()
    last = Record(len(chunks), chunks[-1][:2].decode("latin-1").ljust(2), [])
    if rf is None:
        checker.error(last, 1, 2, "file does not end with an RF (Final) Record")
    else:
        if rf.amount("Number of RW Records") != total_rw:
            checker.error(rf, rf.by_name["Number of RW Records"], f"says {rf.amount('Number of RW Records')}, file has {total_rw} RW records")
    if total_rw > MAX_RW_PER_SUBMISSION:
        checker.warn(last, 1, 2, f"{total_rw} RW records exceed the {MAX_RW_PER_SUBMISSION:,} per-submission guideline")
    if re_count > MAX_RE_PER_SUBMISSION:
        checker.warn(last, 1, 2, f"{re_count} RE records exceed the {MAX_RE_PER_SUBMISSION:,} per-submission guideline")
    return checker.findings


def use_tax_year(year):
    global TAX_YEAR, SOCIAL_SECURITY_WAGE_BASE_CENTS, SOCIAL_SECURITY_MAX_TAX_CENTS
    TAX_YEAR = year
    SOCIAL_SECURITY_WAGE_BASE_CENTS = WAGE_BASES_CENTS[year]
    SOCIAL_SECURITY_MAX_TAX_CENTS = round(SOCIAL_SECURITY_WAGE_BASE_CENTS * SOCIAL_SECURITY_RATE)


def main(args):
    paths = list(args)
    if paths[:1] == ["--tax-year"] and len(paths) >= 2 and paths[1].isdigit() and int(paths[1]) in WAGE_BASES_CENTS:
        use_tax_year(int(paths[1]))
        paths = paths[2:]
    if not paths or paths[0].startswith("--"):
        print("usage: python3 efw2_check.py [--tax-year 2025|2026] FILE...", file=sys.stderr)
        return 2
    any_error = False
    for path in paths:
        print(f"== {path}")
        try:
            findings = check_file(path)
        except OSError as exc:
            print(f"ERROR 0 -- -: cannot read file: {exc}")
            any_error = True
            continue
        findings.sort(key=lambda f: f.number)
        for finding in findings:
            print(f"{finding.level} {finding.number} {finding.record_id} {finding.positions}: {finding.message}")
        errors = sum(1 for f in findings if f.level == "ERROR")
        warnings = len(findings) - errors
        any_error = any_error or errors > 0
        print("OK" if not findings else f"{errors} error(s), {warnings} warning(s)")
    return 1 if any_error else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
