package com.irs.mef.ssnvs;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * SSA's name rule (letters only; no hyphens, apostrophes, spaces, periods, suffixes or prefixes) as one
 * tokenizer. Affixes are dropped while the name is still tokenized: dropping them after the spaces are
 * gone would turn SMITH JR into SMITHJR.
 */
final class SsaNameRules {

    /**
     * Hyphen U+002D and hyphens U+2010 through U+2015, plus apostrophes U+0027, U+2018, U+2019, U+02BC,
     * U+0060 and U+00B4. The trailing hyphen is literal; U+2010-U+2015 is the other hyphen range.
     * Every other non-letter holds the person.
     */
    private static final String DELETED_PUNCTUATION = "['\u2018\u2019\u02BC\u0060\u00B4\u2010-\u2015-]";
    /** Unicode separators (including no-break space), ASCII whitespace, comma and period. */
    private static final String TOKEN_SEPARATORS = "[\\p{Z}\\s,.]+";

    private SsaNameRules() {
    }

    /** {@code value} is null when nothing sendable is left; the holdbacks then say why. */
    record NormalizedName(String value, EnumSet<Holdback> holdbacks, EnumSet<Advisory> advisories) {
        @Override
        public String toString() {
            return "NormalizedName[holdbacks=" + holdbacks + ", advisories=" + advisories + "]";
        }
    }

    /** Tokens after affix dropping. {@code toString} omits the tokens, which are name text. */
    record NameTokens(List<String> tokens, boolean affixDropped) {
        @Override
        public String toString() {
            return "NameTokens[count=" + tokens.size() + ", affixDropped=" + affixDropped + "]";
        }
    }

    /**
     * NFD, drop combining marks, uppercase, split, then drop this part's affixes while another token remains.
     */
    static NameTokens tokens(String raw, NamePart part) {
        String folded = Normalizer.normalize(raw == null ? "" : raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
        List<String> words = new ArrayList<>(Arrays.asList(folded.split(TOKEN_SEPARATORS)));
        words.removeIf(String::isEmpty);
        boolean dropped = dropAffixes(words, part);
        return new NameTokens(List.copyOf(words), dropped);
    }

    static NormalizedName normalize(String raw, NamePart part) {
        EnumSet<Holdback> holdbacks = EnumSet.noneOf(Holdback.class);
        EnumSet<Advisory> advisories = EnumSet.noneOf(Advisory.class);
        NameTokens parsed = tokens(raw, part);
        if (parsed.affixDropped()) {
            advisories.add(Advisory.SUFFIX_OR_PREFIX_DROPPED);
        }
        String joined = String.join("", parsed.tokens()).replaceAll(DELETED_PUNCTUATION, "");
        if (!joined.matches("[A-Z]*")) {
            holdbacks.add(Holdback.NAME_INVALID_CHARACTER);
            return new NormalizedName(null, holdbacks, advisories);
        }
        if (joined.isEmpty()) {
            if (part.blankHoldback != null) {
                holdbacks.add(part.blankHoldback);
            }
            return new NormalizedName(null, holdbacks, advisories);
        }
        if (joined.length() > part.width) {
            advisories.add(Advisory.NAME_TRUNCATED);
            joined = joined.substring(0, part.width);
        }
        return new NormalizedName(joined, holdbacks, advisories);
    }

    private static boolean dropAffixes(List<String> words, NamePart part) {
        boolean dropped = false;
        while (words.size() > 1) {
            int edge = part.affixEnd == NamePart.AffixEnd.TRAILING ? words.size() - 1 : 0;
            if (!part.affixes.contains(words.get(edge))) {
                break;
            }
            words.remove(edge);
            dropped = true;
        }
        return dropped;
    }
}
