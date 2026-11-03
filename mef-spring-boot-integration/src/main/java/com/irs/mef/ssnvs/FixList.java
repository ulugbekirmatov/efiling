package com.irs.mef.ssnvs;

import com.irs.mef.ssnvs.SsnvsInputException.Problem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Pure reconciliation of one EIN's sent records with SSA's results. A row for every person who is not
 * (verified and alive) and for every integrity problem; an empty list means the EFW2 file can be built.
 */
record FixList(List<FixRow> rows, int sent, int verifiedAlive) {

    static final List<String> COLUMNS = List.of("employee_id", "w2_form_ids", "company_codes", "verification_code",
            "meaning", "death", "name_source", "birth_date_sent", "action");

    /**
     * One fixes.csv row. {@code person} is null for a results line whose control was not sent and for a
     * file-level problem; {@code death} is null for integrity rows; {@code resultsLine} is 0 when no line applies.
     */
    record FixRow(Manifest.Person person, Finding finding, Death death, int resultsLine) {

        List<String> cells() {
            return List.of(
                    person == null ? "" : Long.toString(person.employeeId()),
                    person == null ? "" : Objects.toString(person.w2FormIds(), ""),
                    person == null ? "" : Objects.toString(person.companyCodes(), ""),
                    finding.name(),
                    finding.meaning(),
                    death == null ? "" : death.flag(),
                    person == null ? "" : person.nameSource().name(),
                    person == null ? "" : (person.birthDateSent() ? "Y" : "N"),
                    action());
        }

        String action() {
            Stream<String> parts = finding instanceof Integrity
                    ? Stream.of(finding.action(), resultsLine > 0 ? "(results line " + resultsLine + ")" : "")
                    : Stream.of(finding.action(), death == null ? "" : death.action());
            return parts.filter(part -> !part.isEmpty()).collect(Collectors.joining(" "));
        }

        long sortKey() {
            return person == null ? Long.MAX_VALUE : person.employeeId();
        }
    }

    static FixList reconcile(List<SsnvsRequest> sentRecords, Manifest manifest, List<SsnvsResult> results) {
        Map<String, SsnvsRequest> sentByControl = new HashMap<>();
        sentRecords.forEach(request -> sentByControl.put(request.control().text(), request));
        if (!sentByControl.keySet().equals(manifest.people().keySet())) {
            throw new SsnvsInputException(Problem.MANIFEST_MISMATCH, 0, SsnvsOutput.MANIFEST);
        }
        Map<String, List<SsnvsResult>> resultsByControl = new LinkedHashMap<>();
        List<FixRow> rows = new ArrayList<>();
        for (SsnvsResult result : results) {
            if (manifest.people().containsKey(result.control())) {
                resultsByControl.computeIfAbsent(result.control(), control -> new ArrayList<>()).add(result);
            } else {
                rows.add(new FixRow(null, Integrity.UNKNOWN_CONTROL, null, result.line()));
            }
        }
        int verifiedAlive = 0;
        for (Map.Entry<String, Manifest.Person> entry : manifest.people().entrySet()) {
            Manifest.Person person = entry.getValue();
            List<SsnvsResult> lines = resultsByControl.getOrDefault(entry.getKey(), List.of());
            if (lines.isEmpty()) {
                rows.add(new FixRow(person, Integrity.MISSING_FROM_RESULTS, null, 0));
            } else if (lines.size() > 1) {
                lines.forEach(line -> rows.add(new FixRow(person, Integrity.DUPLICATE_RESULT, null, line.line())));
            } else if (!lines.get(0).echoes(sentByControl.get(entry.getKey()))) {
                rows.add(new FixRow(person, Integrity.ECHO_MISMATCH, null, lines.get(0).line()));
            } else if (lines.get(0).verification() == Verification.VERIFIED && lines.get(0).death() == Death.NO) {
                verifiedAlive++;
            } else {
                rows.add(new FixRow(person, lines.get(0).verification(), lines.get(0).death(), lines.get(0).line()));
            }
        }
        confirmationProblem(results).ifPresent(rows::add);
        rows.sort(Comparator.comparingLong(FixRow::sortKey).thenComparingInt(FixRow::resultsLine));
        return new FixList(List.copyOf(rows), sentRecords.size(), verifiedAlive);
    }

    /** One upload gets one confirmation number; two in one file means files were concatenated. */
    private static Optional<FixRow> confirmationProblem(List<SsnvsResult> results) {
        String firstConfirmation = null;
        for (SsnvsResult result : results) {
            if (result.confirmation().isEmpty()) {
                continue;
            }
            if (firstConfirmation == null) {
                firstConfirmation = result.confirmation();
            } else if (!firstConfirmation.equals(result.confirmation())) {
                return Optional.of(new FixRow(null, Integrity.CONFIRMATION_DIFFERS, null, result.line()));
            }
        }
        return Optional.empty();
    }

    boolean clean() {
        return rows.isEmpty();
    }
}
