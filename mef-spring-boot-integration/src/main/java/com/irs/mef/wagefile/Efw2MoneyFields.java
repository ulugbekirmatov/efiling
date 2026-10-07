package com.irs.mef.wagefile;

import java.util.List;

/**
 * RW money columns and the RT total that must equal their sum.
 * Positions are Publication 42-007, tax year 2026, 1-based inclusive.
 */
final class Efw2MoneyFields {

    record Column(String name, int rwStart, int rwEnd, int rtStart, int rtEnd) {
    }

    static final List<Column> COLUMNS = List.of(
            new Column("wages", 188, 198, 10, 24),
            new Column("federal income tax withheld", 199, 209, 25, 39),
            new Column("social security wages", 210, 220, 40, 54),
            new Column("social security tax withheld", 221, 231, 55, 69),
            new Column("medicare wages and tips", 232, 242, 70, 84),
            new Column("medicare tax withheld", 243, 253, 85, 99),
            new Column("social security tips", 254, 264, 100, 114),
            new Column("dependent care benefits", 276, 286, 130, 144),
            new Column("deferred compensation 401(k)", 287, 297, 145, 159),
            new Column("deferred compensation 403(b)", 298, 308, 160, 174),
            new Column("deferred compensation 408(k)(6)", 309, 319, 175, 189),
            new Column("deferred compensation 457(b)", 320, 330, 190, 204),
            new Column("deferred compensation 501(c)(18)(D)", 331, 341, 205, 219),
            new Column("nonqualified plan section 457", 353, 363, 235, 249),
            new Column("health savings account", 364, 374, 250, 264),
            new Column("nonqualified plan not section 457", 375, 385, 265, 279),
            new Column("nontaxable combat pay", 386, 396, 280, 294),
            new Column("group term life insurance", 408, 418, 310, 324),
            new Column("nonstatutory stock options", 419, 429, 340, 354),
            new Column("section 409A deferrals", 430, 440, 355, 369),
            new Column("designated roth 401(k)", 441, 451, 370, 384),
            new Column("designated roth 403(b)", 452, 462, 385, 399),
            new Column("employer sponsored health coverage", 463, 473, 295, 309),
            new Column("qualified small employer hra", 474, 484, 400, 414));

    /**
     * RT 325-339, income tax withheld by a third-party sick-pay payer.
     * The RW section of the extract has no matching money field, so a v1 file reports zeros.
     */
    static final int THIRD_PARTY_SICK_PAY_TAX_START = 325;
    static final int THIRD_PARTY_SICK_PAY_TAX_END = 339;

    private Efw2MoneyFields() {
    }
}
