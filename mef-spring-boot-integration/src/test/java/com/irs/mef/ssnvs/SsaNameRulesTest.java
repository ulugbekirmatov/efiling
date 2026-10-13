package com.irs.mef.ssnvs;

import com.irs.mef.ssnvs.SsaNameRules.NormalizedName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SsaNameRulesTest {

    private static NormalizedName sent(String value, Advisory... advisories) {
        EnumSet<Advisory> expected = EnumSet.noneOf(Advisory.class);
        expected.addAll(List.of(advisories));
        return new NormalizedName(value, EnumSet.noneOf(Holdback.class), expected);
    }

    private static NormalizedName held(Holdback holdback) {
        return new NormalizedName(null, EnumSet.of(holdback), EnumSet.noneOf(Advisory.class));
    }

    @Test
    @DisplayName("a suffix is dropped before the spaces go, then hyphen and apostrophe are deleted")
    void suffixHyphenApostrophe() {
        assertEquals(sent("OBRIENSMITH", Advisory.SUFFIX_OR_PREFIX_DROPPED),
                SsaNameRules.normalize("O'Brien-Smith Jr.", NamePart.LAST));
        assertEquals(sent("ZQLOBRIX", Advisory.SUFFIX_OR_PREFIX_DROPPED),
                SsaNameRules.normalize("zql o\u2019brix III", NamePart.LAST));
    }

    @Test
    @DisplayName("a two-word first name is sent with no space")
    void twoWordFirstName() {
        assertEquals(sent("MARYANN"), SsaNameRules.normalize("Mary Ann", NamePart.FIRST));
    }

    @Test
    @DisplayName("an accented E folds to E")
    void accentFolds() {
        assertEquals(sent("ZQRENE"), SsaNameRules.normalize("Zqr\u00e9n\u00c9", NamePart.FIRST));
    }

    @Test
    @DisplayName("a leading title is dropped from the first name")
    void titleDropped() {
        assertEquals(sent("JOHN", Advisory.SUFFIX_OR_PREFIX_DROPPED), SsaNameRules.normalize("Dr. John", NamePart.FIRST));
    }

    @Test
    @DisplayName("a 20-letter last name is truncated to 13")
    void truncated() {
        assertEquals(sent("ZQLABCDEFGHIJ", Advisory.NAME_TRUNCATED),
                SsaNameRules.normalize("ZQLABCDEFGHIJKLMNOPQ", NamePart.LAST));
        assertEquals(sent("ZQMABCD", Advisory.NAME_TRUNCATED), SsaNameRules.normalize("ZQM ABCDE", NamePart.MIDDLE));
    }

    @Test
    @DisplayName("a digit, a symbol or a non-Latin letter holds the person")
    void invalidCharacters() {
        assertEquals(held(Holdback.NAME_INVALID_CHARACTER), SsaNameRules.normalize("ZQL2", NamePart.LAST));
        assertEquals(held(Holdback.NAME_INVALID_CHARACTER), SsaNameRules.normalize("ZQ&F", NamePart.FIRST));
        assertEquals(held(Holdback.NAME_INVALID_CHARACTER), SsaNameRules.normalize("ZQ\u0416", NamePart.LAST));
    }

    @Test
    @DisplayName("a suffix or title that is the whole name stays")
    void loneAffixStays() {
        assertEquals(sent("JR"), SsaNameRules.normalize("Jr", NamePart.LAST));
        assertEquals(sent("DR"), SsaNameRules.normalize("DR", NamePart.FIRST));
    }

    @Test
    @DisplayName("nothing left after normalization holds the person, except an empty middle name")
    void blankAfterNormalization() {
        assertEquals(held(Holdback.LAST_NAME_BLANK), SsaNameRules.normalize(" - ", NamePart.LAST));
        assertEquals(held(Holdback.FIRST_NAME_BLANK), SsaNameRules.normalize(".", NamePart.FIRST));
        assertEquals(new NormalizedName(null, EnumSet.noneOf(Holdback.class), EnumSet.noneOf(Advisory.class)),
                SsaNameRules.normalize("'", NamePart.MIDDLE));
    }
}
