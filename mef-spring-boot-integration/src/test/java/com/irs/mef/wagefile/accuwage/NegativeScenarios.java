package com.irs.mef.wagefile.accuwage;

import com.irs.mef.wagefile.Efw2Amount;
import com.irs.mef.wagefile.Efw2Writer;
import com.irs.mef.wagefile.EmployeeWage;
import com.irs.mef.wagefile.FixedWidth;
import com.irs.mef.wagefile.WageYear;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.irs.mef.wagefile.accuwage.AccuWageScenario.AccuWageOutcome.ERRORS;
import static com.irs.mef.wagefile.accuwage.ScenarioKit.employer;
import static com.irs.mef.wagefile.accuwage.ScenarioKit.pay;
import static com.irs.mef.wagefile.accuwage.ScenarioKit.person;

/**
 * Files that are wrong on purpose, each with the AccuWage error it should raise.
 * Every file is one clean base file with exactly one defect patched in. A money patch on an RW record
 * also moves the RT total by the same amount, so the totals still balance and only the RW rule breaks.
 * Rules quote Publication 42-007 and SSA's Online Error Reference; SSA does not publish AccuWage's
 * message text, so expected results name the rule and the error condition instead.
 */
final class NegativeScenarios {

    private static final String CRLF = "\r\n";
    private static final String PUB_42_007 = "Pub 42-007 (https://www.ssa.gov/employer/efw/)";
    private static final String ERROR_REFERENCE = "https://www.ssa.gov/employer/onlineerror.htm";

    private static final int RA = 0;
    private static final int RE = 1;
    private static final int FIRST_RW = 2;
    private static final int SECOND_RW = 3;
    private static final int RT = 5;
    private static final int RF = 6;

    private static final long SOCIAL_SECURITY_TAX_MAXIMUM_CENTS = 11_439_00L;

    private NegativeScenarios() {
    }

    static List<AccuWageScenario> scenarios() {
        String base = baseFile();
        return List.of(
                negative("90-ssn-area-666.txt",
                        "RW 1 SSN begins with 666.",
                        "Error on RW 1 Social Security Number: " + PUB_42_007 + " SSN rule \"May not begin with a"
                                + " 666 or 9.\"",
                        patch(base, FIRST_RW, 3, 11, "666" + field(base, FIRST_RW, 6, 11))),
                negative("91-social-security-wages-over-base.txt",
                        "RW 2 Social Security wages are $184,500.01, one cent over the TY2026 wage base.",
                        "Error on RW 2 Social Security Wages: " + PUB_42_007 + " RW 210-220 \"The sum of this field"
                                + " and the Social Security Tips field should not exceed the annual maximum Social"
                                + " Security wage base for the tax year\"; SSA condition \"Social Security Wages / Tips"
                                + " Amounts Invalid for the TY\" " + ERROR_REFERENCE,
                        patchMoney(base, SECOND_RW, Efw2Amount.SOCIAL_SECURITY_WAGES,
                                ScenarioKit.SOCIAL_SECURITY_WAGE_BASE_CENTS + 1)),
                negative("92-social-security-tax-over-maximum.txt",
                        "RW 2 Social Security tax is $11,439.01, one cent over the TY2026 maximum.",
                        "Error on RW 2 Social Security Tax Withheld: " + PUB_42_007 + " RW 221-231 \"This amount"
                                + " should not exceed\" the year's maximum ($11,439.00 for TY2026)",
                        patchMoney(base, SECOND_RW, Efw2Amount.SOCIAL_SECURITY_TAX_WITHHELD,
                                SOCIAL_SECURITY_TAX_MAXIMUM_CENTS + 1)),
                negative("93-medicare-wages-below-social-security-wages.txt",
                        "RW 1 Medicare wages are $47,000.00, below its $48,000.00 Social Security wages.",
                        "Error on RW 1 Medicare Wages and Tips: " + PUB_42_007 + " RW 232-242 \"this amount must"
                                + " equal or exceed the sum of the Social Security Wages and Social Security Tips\";"
                                + " SSA condition \"Medicare Wages & Tips Less Than Social Security Wages/Social"
                                + " Security Tips\" " + ERROR_REFERENCE,
                        patchMoney(base, FIRST_RW, Efw2Amount.MEDICARE_WAGES_AND_TIPS, 47_000_00L)),
                negative("94-rt-wages-out-of-balance.txt",
                        "RT Total Wages, Tips and Other Compensation is $100.00 more than the sum of the RW records.",
                        "Error on RT Total Wages, Tips and Other Compensation: " + PUB_42_007 + " RT 10-24 \"Enter"
                                + " the total for all RW (Employee) Records reported since the last RE (Employer)"
                                + " Record\"; SSA condition \"Out of Balance Money Field(s)\" " + ERROR_REFERENCE,
                        patch(base, RT, 10, 24, FixedWidth.zeroFill(amount(base, RT, 10, 24) + 100_00L, 15))),
                negative("95-rf-employee-count-wrong.txt",
                        "RF Number of RW Records says 4; the file has 3.",
                        "Error on RF Number of RW Records: " + PUB_42_007 + " RF 8-16 \"Enter the total number of"
                                + " RW (Employee) Records reported on the entire file.\"",
                        patch(base, RF, 8, 16, FixedWidth.zeroFill(4, 9))),
                negative("96-missing-rf-record.txt",
                        "The RF record is deleted; the file ends after RT.",
                        "Critical, testing stops: record sequence. " + PUB_42_007 + " \"The file should contain only"
                                + " one submission, beginning with an RA (Submitter) Record and ending with an RF"
                                + " (Final) Record.\"; SSA condition \"Invalid Record Sequence\" " + ERROR_REFERENCE,
                        withoutRecord(base, RF)),
                negative("97-record-511-bytes.txt",
                        "RW 1 is 511 bytes: its last blank byte (position 512) is cut.",
                        "Critical, testing stops: record length. " + PUB_42_007 + " \"The record length must be"
                                + " exactly 512 bytes.\"; SSA condition \"Invalid Record Length\" " + ERROR_REFERENCE,
                        truncated(base, FIRST_RW)),
                negative("98-employer-ein-prefix-07.txt",
                        "RE Employer EIN begins with 07, a prefix SSA does not accept.",
                        "Error on RE Employer/Agent EIN: " + PUB_42_007 + " \"Do not begin with 00, 07, 08, 09, 17,"
                                + " 18, 19, 28, 29, 49, 69, 70, 78, 79 or 89.\"; SSA condition \"Invalid Employer"
                                + " Identification Number (EIN)\" " + ERROR_REFERENCE,
                        patch(base, RE, 8, 16, "07" + field(base, RE, 10, 16))),
                negative("99-ra-contact-email-blank.txt",
                        "RA Contact E-Mail/Internet (446-485) is blank.",
                        "Error on RA Contact E-Mail/Internet: " + PUB_42_007 + " RA 446-485 \"This is a required"
                                + " field.\" and \"Must not be blank\"",
                        patch(base, RA, 446, 485, " ".repeat(40))));
    }

    /** One employer, three employees. RW 2 earns past the wage base so its Social Security boxes sit at the cap. */
    private static String baseFile() {
        List<EmployeeWage> employees = List.of(
                pay(48_000_00L).federalIncomeTax(3_960_00L).w2(person(40, "YORK", "PA", "17401")),
                pay(210_000_00L).federalIncomeTax(41_250_00L).w2(person(41, "RED LION", "PA", "17356")),
                pay(63_400_25L).federalIncomeTax(6_118_40L).w2(person(42, "HANOVER", "PA", "17331")));
        WageYear year = new WageYear(ScenarioKit.submitter(), List.of(
                employer("461234567", "SUSQUEHANNA DENTAL GROUP PC", "55 MARKET ST", "YORK", "PA", "17401",
                        employees)));
        return new Efw2Writer().write(year);
    }

    private static AccuWageScenario negative(String fileName, String purpose, String expectedResult, String text) {
        return new AccuWageScenario(fileName, purpose, ERRORS, expectedResult, text);
    }

    private static String patch(String file, int recordIndex, int start, int end, String value) {
        List<String> records = records(file);
        FixedWidth record = FixedWidth.parse(records.get(recordIndex));
        record.put(start, end, value);
        records.set(recordIndex, record.text());
        return join(records);
    }

    /** Sets an RW amount and moves its RT total by the same delta, so only the RW rule breaks. */
    private static String patchMoney(String file, int rwIndex, Efw2Amount money, long cents) {
        long delta = cents - amount(file, rwIndex, money.rwStart(), money.rwEnd());
        String patched = patch(file, rwIndex, money.rwStart(), money.rwEnd(), FixedWidth.zeroFill(cents, 11));
        long total = amount(patched, RT, money.rtStart(), money.rtEnd()) + delta;
        return patch(patched, RT, money.rtStart(), money.rtEnd(), FixedWidth.zeroFill(total, 15));
    }

    private static String field(String file, int recordIndex, int start, int end) {
        return FixedWidth.parse(records(file).get(recordIndex)).get(start, end);
    }

    private static long amount(String file, int recordIndex, int start, int end) {
        return Long.parseLong(field(file, recordIndex, start, end));
    }

    private static String withoutRecord(String file, int recordIndex) {
        List<String> records = records(file);
        records.remove(recordIndex);
        return join(records);
    }

    private static String truncated(String file, int recordIndex) {
        List<String> records = records(file);
        String record = records.get(recordIndex);
        records.set(recordIndex, record.substring(0, record.length() - 1));
        return join(records);
    }

    private static List<String> records(String file) {
        List<String> records = new ArrayList<>(Arrays.asList(file.split(CRLF, -1)));
        records.remove(records.size() - 1);
        return records;
    }

    private static String join(List<String> records) {
        return String.join(CRLF, records) + CRLF;
    }
}
