package com.irs.mef.wagefile.accuwage;

import com.irs.mef.wagefile.Efw2Amount;
import com.irs.mef.wagefile.EmployeeWage;
import com.irs.mef.wagefile.EmployerWage;
import com.irs.mef.wagefile.Submitter;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds W-2 rows from gross pay so every scenario gets the box arithmetic right once.
 * Tax year defaults to 2026; -Daccuwage.taxYear=2025 builds the same scenarios for AccuWage before it
 * accepts TY2026 (the RA/RE/RW/RT/RF fields written here did not move between the two years).
 * Submitter EIN and BSO User ID default to placeholders; pass -Daccuwage.submitterEin and
 * -Daccuwage.bsoUserId to stamp real values.
 */
final class ScenarioKit {

    private static final Map<Integer, Long> SOCIAL_SECURITY_WAGE_BASES_CENTS = Map.of(
            2025, 176_100_00L,
            2026, 184_500_00L);

    static final int TAX_YEAR = Integer.parseInt(System.getProperty("accuwage.taxYear", "2026"));
    static final long SOCIAL_SECURITY_WAGE_BASE_CENTS = wageBase(TAX_YEAR);
    static final long SOCIAL_SECURITY_TAX_MAXIMUM_CENTS = percentOf(SOCIAL_SECURITY_WAGE_BASE_CENTS, 62, 1000);
    static final long ADDITIONAL_MEDICARE_THRESHOLD_CENTS = 200_000_00L;

    private static final String[] FIRST_NAMES = {"JAMES", "MARIA", "ROBERT", "LINDA", "DAVID", "PATRICIA",
            "MICHAEL", "JENNIFER", "WILLIAM", "ELIZABETH", "JOSEPH", "SUSAN", "THOMAS", "JESSICA", "CHARLES"};
    private static final String[] LAST_NAMES = {"SMITH", "JOHNSON", "WILLIAMS", "BROWN", "JONES", "GARCIA",
            "MILLER", "DAVIS", "RODRIGUEZ", "MARTINEZ", "HERNANDEZ", "LOPEZ", "WILSON", "ANDERSON", "TAYLOR"};

    private ScenarioKit() {
    }

    private static long wageBase(int taxYear) {
        Long cents = SOCIAL_SECURITY_WAGE_BASES_CENTS.get(taxYear);
        if (cents == null) {
            throw new IllegalArgumentException("no Social Security wage base for tax year " + taxYear);
        }
        return cents;
    }

    static String dollars(long cents) {
        return String.format(Locale.ROOT, "$%,d.%02d", cents / 100, cents % 100);
    }

    static Submitter submitter() {
        return new Submitter(
                System.getProperty("accuwage.submitterEin", "123456789"),
                System.getProperty("accuwage.bsoUserId", "PYRAMOS1"),
                "PYRAMOS SOFTWARE LLC",
                "",
                "100 MARKET ST",
                "HARRISBURG",
                "PA",
                "17101",
                "PYRAMOS SOFTWARE LLC",
                "W2 CONTACT",
                "7175550199",
                "w2@pyramos.example.com");
    }

    static EmployerWage employer(String ein, String name, String deliveryAddress, String city, String state,
                                 String zip, List<EmployeeWage> employees) {
        return new EmployerWage(TAX_YEAR, ein, name, deliveryAddress, city, state, zip, "PAYROLL DEPT",
                "7175550100", "payroll@" + ein + ".example.com", employees);
    }

    record Person(String ssn, String firstName, String middleName, String lastName, String deliveryAddress,
                  String city, String state, String zip) {
    }

    /**
     * Deterministic test person number {@code n} (0-8999). The SSN avoids area 000, 666 and 900-999,
     * group 00 and serial 0000, so AccuWage reads it as well formed.
     */
    static Person person(int n, String city, String state, String zip) {
        String ssn = String.format("%03d%02d%04d", 200 + n % 400, 10 + n % 89, 1000 + n);
        return new Person(ssn, FIRST_NAMES[n % FIRST_NAMES.length], "",
                LAST_NAMES[(n / FIRST_NAMES.length) % LAST_NAMES.length],
                (100 + n) + " OAK ST", city, state, zip);
    }

    static Pay pay(long grossCents) {
        return new Pay(grossCents);
    }

    /** A year of pay for one employee. Amounts are cents; each setter returns this. */
    static final class Pay {
        private final long grossCents;
        private long federalIncomeTaxWithheldCents;
        private long section125Cents;
        private long deferral401kCents;
        private long roth401kCents;
        private long healthSavingsAccountCents;
        private long healthCoverageCents;
        private long groupTermLifeCents;
        private long tipsCents;
        private long dependentCareCents;

        private Pay(long grossCents) {
            this.grossCents = grossCents;
        }

        Pay federalIncomeTax(long cents) {
            federalIncomeTaxWithheldCents = cents;
            return this;
        }

        /** Cafeteria plan premiums: excluded from Boxes 1, 3 and 5. */
        Pay section125(long cents) {
            section125Cents = cents;
            return this;
        }

        /** Box 12 D: excluded from Box 1 only. */
        Pay deferral401k(long cents) {
            deferral401kCents = cents;
            return this;
        }

        /** Box 12 AA: after tax, stays in every wage box. */
        Pay roth401k(long cents) {
            roth401kCents = cents;
            return this;
        }

        /** Box 12 W, employer and cafeteria HSA contributions: excluded from Boxes 1, 3 and 5. */
        Pay healthSavingsAccount(long cents) {
            healthSavingsAccountCents = cents;
            return this;
        }

        /** Box 12 DD: informational, no wage effect. */
        Pay healthCoverage(long cents) {
            healthCoverageCents = cents;
            return this;
        }

        /** Box 12 C, imputed group-term life over $50,000: added to Boxes 1, 3 and 5. */
        Pay groupTermLife(long cents) {
            groupTermLifeCents = cents;
            return this;
        }

        /** Box 7: in Boxes 1 and 5, reported apart from Box 3. */
        Pay tips(long cents) {
            tipsCents = cents;
            return this;
        }

        /** Box 10, up to the $5,000 exclusion: no wage effect in this kit. */
        Pay dependentCare(long cents) {
            dependentCareCents = cents;
            return this;
        }

        EmployeeWage w2(Person person) {
            long ficaWagesBeforeTips = grossCents + groupTermLifeCents - section125Cents - healthSavingsAccountCents;
            long wages = ficaWagesBeforeTips + tipsCents - deferral401kCents;
            long socialSecurityWages = Math.min(ficaWagesBeforeTips, SOCIAL_SECURITY_WAGE_BASE_CENTS);
            long socialSecurityTips = Math.min(tipsCents, SOCIAL_SECURITY_WAGE_BASE_CENTS - socialSecurityWages);
            long medicareWages = ficaWagesBeforeTips + tipsCents;
            Map<Efw2Amount, Long> others = new EnumMap<>(Efw2Amount.class);
            putIfPositive(others, Efw2Amount.SOCIAL_SECURITY_TIPS, socialSecurityTips);
            putIfPositive(others, Efw2Amount.DEFERRED_COMPENSATION_401K, deferral401kCents);
            putIfPositive(others, Efw2Amount.DESIGNATED_ROTH_401K, roth401kCents);
            putIfPositive(others, Efw2Amount.HEALTH_SAVINGS_ACCOUNT, healthSavingsAccountCents);
            putIfPositive(others, Efw2Amount.EMPLOYER_SPONSORED_HEALTH_COVERAGE, healthCoverageCents);
            putIfPositive(others, Efw2Amount.GROUP_TERM_LIFE_INSURANCE, groupTermLifeCents);
            putIfPositive(others, Efw2Amount.DEPENDENT_CARE_BENEFITS, dependentCareCents);
            return new EmployeeWage(person.ssn(), person.firstName(), person.middleName(), person.lastName(),
                    person.deliveryAddress(), person.city(), person.state(), person.zip(),
                    wages,
                    federalIncomeTaxWithheldCents,
                    socialSecurityWages,
                    percentOf(socialSecurityWages + socialSecurityTips, 62, 1000),
                    medicareWages,
                    percentOf(medicareWages, 145, 10000)
                            + percentOf(Math.max(0, medicareWages - ADDITIONAL_MEDICARE_THRESHOLD_CENTS), 9, 1000),
                    others,
                    deferral401kCents > 0 || roth401kCents > 0);
        }
    }

    /** {@code cents * numerator / denominator}, rounded half up. */
    static long percentOf(long cents, long numerator, long denominator) {
        return (cents * numerator + denominator / 2) / denominator;
    }

    private static void putIfPositive(Map<Efw2Amount, Long> amounts, Efw2Amount amount, long cents) {
        if (cents > 0) {
            amounts.put(amount, cents);
        }
    }
}
