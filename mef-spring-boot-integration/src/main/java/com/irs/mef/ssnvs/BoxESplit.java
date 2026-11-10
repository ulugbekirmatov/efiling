package com.irs.mef.ssnvs;

import com.irs.mef.ssnvs.SsaNameRules.NameTokens;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Cuts box e first_middle (one string on the W-2) into first and middle. Box e is what is sent, because SSA
 * must verify the W-2 name; the HR and ADP masters only decide where to cut. Comparison uses
 * {@link SsaNameRules#tokens}, so accents, case and affixes do not block a master match. {@code middle} is
 * null when there is none.
 */
record BoxESplit(String first, String middle, NameSource source, boolean affixDropped) {

    /** A master's name parts, tried in order: the first one whose first + middle and last equal box e wins. */
    private record MasterName(NameSource source, Function<W2PersonRow, String> first,
                              Function<W2PersonRow, String> middle, Function<W2PersonRow, String> last) {
    }

    private static final List<MasterName> MASTER_NAMES = List.of(
            new MasterName(NameSource.HR_FULL, W2PersonRow::hrFirstName, W2PersonRow::hrMiddleName,
                    W2PersonRow::hrLastName),
            new MasterName(NameSource.HR_INITIAL, W2PersonRow::hrFirstName, W2PersonRow::hrMiddleInitial,
                    W2PersonRow::hrLastName),
            new MasterName(NameSource.ADP_FULL, W2PersonRow::adpFirstName, W2PersonRow::adpMiddleName,
                    W2PersonRow::adpLastName));

    @Override
    public String toString() {
        return "BoxESplit[source=" + source + "]";
    }

    /** Null when box e first_middle holds no token at all. */
    static BoxESplit of(W2PersonRow row) {
        NameTokens leading = SsaNameRules.tokens(row.boxEFirstMiddle(), NamePart.FIRST);
        if (leading.tokens().isEmpty()) {
            return null;
        }
        NameTokens boxE = SsaNameRules.tokens(String.join(" ", leading.tokens()), NamePart.MIDDLE);
        List<String> tokens = boxE.tokens();
        if (tokens.isEmpty()) {
            return null;
        }
        NameTokens boxELast = SsaNameRules.tokens(row.boxELastName(), NamePart.LAST);
        boolean affixDropped = leading.affixDropped() || boxE.affixDropped() || boxELast.affixDropped();
        for (MasterName master : MASTER_NAMES) {
            List<String> masterFirst = SsaNameRules.tokens(master.first().apply(row), NamePart.FIRST).tokens();
            List<String> masterMiddle = SsaNameRules.tokens(master.middle().apply(row), NamePart.MIDDLE).tokens();
            List<String> masterLast = SsaNameRules.tokens(master.last().apply(row), NamePart.LAST).tokens();
            List<String> masterFirstMiddle = new ArrayList<>(masterFirst);
            masterFirstMiddle.addAll(masterMiddle);
            if (!boxELast.tokens().isEmpty() && boxELast.tokens().equals(masterLast)
                    && tokens.equals(masterFirstMiddle)) {
                return new BoxESplit(String.join(" ", masterFirst),
                        masterMiddle.isEmpty() ? null : String.join(" ", masterMiddle), master.source(),
                        affixDropped);
            }
        }
        if (tokens.size() == 1) {
            return new BoxESplit(tokens.get(0), null, NameSource.SINGLE_TOKEN, affixDropped);
        }
        List<String> hrFirst = SsaNameRules.tokens(row.hrFirstName(), NamePart.FIRST).tokens();
        if (!hrFirst.isEmpty() && tokens.equals(hrFirst)) {
            return new BoxESplit(String.join(" ", hrFirst), null, NameSource.HR_FIRST_PREFIX, affixDropped);
        }
        if (!hrFirst.isEmpty() && tokens.size() > hrFirst.size()
                && tokens.subList(0, hrFirst.size()).equals(hrFirst)) {
            return new BoxESplit(String.join(" ", hrFirst),
                    String.join(" ", tokens.subList(hrFirst.size(), tokens.size())), NameSource.HR_FIRST_PREFIX,
                    affixDropped);
        }
        return new BoxESplit(tokens.get(0), String.join(" ", tokens.subList(1, tokens.size())), NameSource.GUESSED,
                affixDropped);
    }
}
