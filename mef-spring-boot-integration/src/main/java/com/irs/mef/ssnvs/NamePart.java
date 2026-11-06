package com.irs.mef.ssnvs;

import java.util.Set;

/**
 * The three SSNVS name fields and the one affix table. Last drops a trailing suffix, first drops a
 * leading title, and middle drops a trailing last-name suffix, each only while another token remains.
 * Middle keeps a lone letter: in box e "JOHN V" the V is a middle initial far more often than a numeral.
 * Middle is optional, so it has no blank holdback.
 */
enum NamePart {
    LAST(13, Set.of("JR", "SR", "II", "III", "IV", "V", "JUNIOR", "SENIOR", "2ND", "3RD", "4TH", "ESQ", "MD",
            "PHD", "DDS"), AffixEnd.TRAILING, Holdback.LAST_NAME_BLANK),
    FIRST(10, Set.of("DR", "MR", "MRS", "MS", "MISS"), AffixEnd.LEADING, Holdback.FIRST_NAME_BLANK),
    MIDDLE(7, Set.of("JR", "SR", "II", "III", "IV", "JUNIOR", "SENIOR", "2ND", "3RD", "4TH", "ESQ", "MD", "PHD",
            "DDS"), AffixEnd.TRAILING, null);

    enum AffixEnd { LEADING, TRAILING }

    final int width;
    final Set<String> affixes;
    final AffixEnd affixEnd;
    final Holdback blankHoldback;

    NamePart(int width, Set<String> affixes, AffixEnd affixEnd, Holdback blankHoldback) {
        this.width = width;
        this.affixes = affixes;
        this.affixEnd = affixEnd;
        this.blankHoldback = blankHoldback;
    }

    boolean accepts(String value) {
        return value != null && value.matches("[A-Z]{1," + width + "}");
    }
}
