package com.irs.mef.ssnvs;

import java.util.Set;

/**
 * The three SSNVS name fields: width on the record, which affix tokens normalization drops,
 * and the holdback when nothing is left. Middle is optional, so it has no blank holdback.
 */
enum NamePart {
    LAST(13, Set.of("JR", "SR", "II", "III", "IV"), AffixEnd.TRAILING, Holdback.LAST_NAME_BLANK),
    FIRST(10, Set.of("DR", "MR", "MRS", "MS", "MISS"), AffixEnd.LEADING, Holdback.FIRST_NAME_BLANK),
    MIDDLE(7, Set.of(), AffixEnd.LEADING, null);

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
