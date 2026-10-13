package com.irs.mef.ssnvs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Cuts box e first_middle (one string on the W-2) into first and middle. Box e is what is sent, because SSA
 * must verify the W-2 name; the HR and ADP masters only decide where to cut. Pieces are folded, not yet
 * normalized. {@code middle} is null when there is none.
 */
record BoxESplit(String first, String middle, NameSource source, boolean prefixDropped) {

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
        List<String> tokens = new ArrayList<>(Arrays.asList(SsaNameRules.fold(row.boxEFirstMiddle()).split(" ")));
        tokens.removeIf(String::isEmpty);
        if (tokens.isEmpty()) {
            return null;
        }
        boolean prefixDropped = false;
        while (tokens.size() > 1 && NamePart.FIRST.affixes.contains(tokens.get(0).replaceAll("[.,]", ""))) {
            tokens.remove(0);
            prefixDropped = true;
        }
        String boxE = String.join(" ", tokens);
        String boxELast = SsaNameRules.fold(row.boxELastName());
        for (MasterName master : MASTER_NAMES) {
            String first = master.first().apply(row);
            String middle = master.middle().apply(row);
            if (first != null && !boxELast.isEmpty()
                    && boxELast.equals(SsaNameRules.fold(master.last().apply(row)))
                    && boxE.equals(SsaNameRules.fold(middle == null ? first : first + " " + middle))) {
                return new BoxESplit(SsaNameRules.fold(first), middle == null ? null : SsaNameRules.fold(middle),
                        master.source(), prefixDropped);
            }
        }
        if (tokens.size() == 1) {
            return new BoxESplit(boxE, null, NameSource.SINGLE_TOKEN, prefixDropped);
        }
        String hrFirst = SsaNameRules.fold(row.hrFirstName());
        if (!hrFirst.isEmpty() && boxE.equals(hrFirst)) {
            return new BoxESplit(hrFirst, null, NameSource.HR_FIRST_PREFIX, prefixDropped);
        }
        if (!hrFirst.isEmpty() && boxE.startsWith(hrFirst + " ")) {
            return new BoxESplit(hrFirst, boxE.substring(hrFirst.length() + 1), NameSource.HR_FIRST_PREFIX,
                    prefixDropped);
        }
        return new BoxESplit(tokens.get(0), String.join(" ", tokens.subList(1, tokens.size())), NameSource.GUESSED,
                prefixDropped);
    }
}
