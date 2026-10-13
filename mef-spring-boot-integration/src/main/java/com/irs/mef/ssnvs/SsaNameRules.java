package com.irs.mef.ssnvs;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * SSA's name rule (letters only; no hyphens, apostrophes, spaces, periods, suffixes or prefixes) as one
 * function with a fixed order. Affixes are dropped while the name is still tokenized: dropping them after
 * the spaces are gone would turn SMITH JR into SMITHJR.
 */
final class SsaNameRules {

    /** Straight and typographic apostrophes plus the hyphen; every other non-letter holds the person. */
    private static final String DELETED_PUNCTUATION = "['\u2019-]";
    private static final String TOKEN_SEPARATORS = "[\\s,.]+";

    private SsaNameRules() {
    }

    /** {@code value} is null when nothing sendable is left; the holdbacks then say why. */
    record NormalizedName(String value, EnumSet<Holdback> holdbacks, EnumSet<Advisory> advisories) {
        @Override
        public String toString() {
            return "NormalizedName[holdbacks=" + holdbacks + ", advisories=" + advisories + "]";
        }
    }

    static NormalizedName normalize(String raw, NamePart part) {
        EnumSet<Holdback> holdbacks = EnumSet.noneOf(Holdback.class);
        EnumSet<Advisory> advisories = EnumSet.noneOf(Advisory.class);
        String folded = Normalizer.normalize(raw == null ? "" : raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .trim();
        List<String> tokens = new ArrayList<>(Arrays.asList(folded.split(TOKEN_SEPARATORS)));
        tokens.removeIf(String::isEmpty);
        if (dropAffixes(tokens, part)) {
            advisories.add(Advisory.SUFFIX_OR_PREFIX_DROPPED);
        }
        String joined = String.join("", tokens).replaceAll(DELETED_PUNCTUATION, "");
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

    /** Uppercase, whitespace collapsed, trimmed: the form box e and the masters are compared in. */
    static String fold(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static boolean dropAffixes(List<String> tokens, NamePart part) {
        boolean dropped = false;
        while (tokens.size() > 1) {
            int edge = part.affixEnd == NamePart.AffixEnd.TRAILING ? tokens.size() - 1 : 0;
            if (!part.affixes.contains(tokens.get(edge))) {
                break;
            }
            tokens.remove(edge);
            dropped = true;
        }
        return dropped;
    }
}
