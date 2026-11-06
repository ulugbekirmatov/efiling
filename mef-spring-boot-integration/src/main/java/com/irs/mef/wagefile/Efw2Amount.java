package com.irs.mef.wagefile;

/**
 * RW money fields and the RT total that must equal their sum.
 * Positions are Publication 42-007, tax year 2026, 1-based inclusive.
 */
public enum Efw2Amount {
    WAGES("wages", 188, 198, 10, 24),
    FEDERAL_INCOME_TAX_WITHHELD("federal income tax withheld", 199, 209, 25, 39),
    SOCIAL_SECURITY_WAGES("social security wages", 210, 220, 40, 54),
    SOCIAL_SECURITY_TAX_WITHHELD("social security tax withheld", 221, 231, 55, 69),
    MEDICARE_WAGES_AND_TIPS("medicare wages and tips", 232, 242, 70, 84),
    MEDICARE_TAX_WITHHELD("medicare tax withheld", 243, 253, 85, 99),
    SOCIAL_SECURITY_TIPS("social security tips", 254, 264, 100, 114),
    DEPENDENT_CARE_BENEFITS("dependent care benefits", 276, 286, 130, 144),
    DEFERRED_COMPENSATION_401K("deferred compensation 401(k)", 287, 297, 145, 159),
    DEFERRED_COMPENSATION_403B("deferred compensation 403(b)", 298, 308, 160, 174),
    DEFERRED_COMPENSATION_408K6("deferred compensation 408(k)(6)", 309, 319, 175, 189),
    DEFERRED_COMPENSATION_457B("deferred compensation 457(b)", 320, 330, 190, 204),
    DEFERRED_COMPENSATION_501C18D("deferred compensation 501(c)(18)(D)", 331, 341, 205, 219),
    NONQUALIFIED_PLAN_SECTION_457("nonqualified plan section 457", 353, 363, 235, 249),
    HEALTH_SAVINGS_ACCOUNT("health savings account", 364, 374, 250, 264),
    NONQUALIFIED_PLAN_NOT_SECTION_457("nonqualified plan not section 457", 375, 385, 265, 279),
    NONTAXABLE_COMBAT_PAY("nontaxable combat pay", 386, 396, 280, 294),
    GROUP_TERM_LIFE_INSURANCE("group term life insurance", 408, 418, 310, 324),
    NONSTATUTORY_STOCK_OPTIONS("nonstatutory stock options", 419, 429, 340, 354),
    SECTION_409A_DEFERRALS("section 409A deferrals", 430, 440, 355, 369),
    DESIGNATED_ROTH_401K("designated roth 401(k)", 441, 451, 370, 384),
    DESIGNATED_ROTH_403B("designated roth 403(b)", 452, 462, 385, 399),
    EMPLOYER_SPONSORED_HEALTH_COVERAGE("employer sponsored health coverage", 463, 473, 295, 309),
    QUALIFIED_SMALL_EMPLOYER_HRA("qualified small employer hra", 474, 484, 400, 414);

    /**
     * RT 325-339, income tax withheld by a third-party sick-pay payer.
     * RW has no matching money field, so the writer reports zeros.
     */
    static final int THIRD_PARTY_SICK_PAY_TAX_START = 325;
    static final int THIRD_PARTY_SICK_PAY_TAX_END = 339;

    private final String label;
    private final int rwStart;
    private final int rwEnd;
    private final int rtStart;
    private final int rtEnd;

    Efw2Amount(String label, int rwStart, int rwEnd, int rtStart, int rtEnd) {
        this.label = label;
        this.rwStart = rwStart;
        this.rwEnd = rwEnd;
        this.rtStart = rtStart;
        this.rtEnd = rtEnd;
    }

    public String label() {
        return label;
    }

    public int rwStart() {
        return rwStart;
    }

    public int rwEnd() {
        return rwEnd;
    }

    public int rtStart() {
        return rtStart;
    }

    public int rtEnd() {
        return rtEnd;
    }
}
