package com.irs.mef.ssnvs;

import com.irs.mef.ssnvs.EmployerPlan.HeldPerson;
import com.irs.mef.ssnvs.EmployerPlan.PersonRefs;
import com.irs.mef.ssnvs.EmployerPlan.SentPerson;
import com.irs.mef.ssnvs.SsaNameRules.NormalizedName;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Pure policy: export rows in, one {@link EmployerPlan} per employer EIN out (SSA takes one EIN per file).
 * Each person is either sent, with advisories, or held back with the reasons. Same rows and same
 * {@code today} give the same plan, so the same request bytes.
 */
public final class SsnvsPlanner {

    private SsnvsPlanner() {
    }

    public static List<EmployerPlan> plan(List<W2PersonRow> rows, LocalDate today) {
        Set<Long> duplicateEmployeeIds = duplicates(rows.stream().map(W2PersonRow::employeeId).toList());
        Map<String, List<W2PersonRow>> byEin = rows.stream()
                .collect(Collectors.groupingBy(row -> digits(row.employerEin()), TreeMap::new, Collectors.toList()));
        List<EmployerPlan> plans = new ArrayList<>();
        byEin.forEach((ein, einRows) -> plans.add(planEmployer(ein, einRows, today, duplicateEmployeeIds)));
        return plans;
    }

    private static EmployerPlan planEmployer(String ein, List<W2PersonRow> rows, LocalDate today,
                                             Set<Long> duplicateEmployeeIds) {
        List<Person> people = rows.stream()
                .map(row -> Person.assess(row, today, duplicateEmployeeIds.contains(row.employeeId())))
                .sorted(Comparator.comparingLong(person -> person.row.employeeId()))
                .toList();
        holdEveryHolderOfADuplicateSsn(people);
        flagDuplicateFirstLast(people.stream().filter(Person::sendable).toList());
        List<SentPerson> sent = people.stream().filter(Person::sendable).map(Person::sent).toList();
        List<HeldPerson> held = people.stream().filter(person -> !person.sendable())
                .map(person -> new HeldPerson(person.refs(), person.holdbacks)).toList();
        boolean inactive = rows.stream().anyMatch(row -> Boolean.FALSE.equals(row.legalCompanyIsActive()));
        return new EmployerPlan(ein, rows.get(0).taxYear(), inactive, sent, held,
                FileGate.check(ein, sent.size()));
    }

    /** SSA cannot tell who owns a number listed twice, and neither can we: every holder waits for the operator. */
    private static void holdEveryHolderOfADuplicateSsn(List<Person> people) {
        Set<String> duplicated = duplicates(people.stream().filter(person -> person.ssn != null)
                .map(person -> person.ssn.digits()).toList());
        people.stream().filter(person -> person.ssn != null && duplicated.contains(person.ssn.digits()))
                .forEach(person -> person.holdbacks.add(Holdback.DUPLICATE_SSN));
    }

    private static void flagDuplicateFirstLast(List<Person> sendable) {
        Map<String, Set<String>> ssnsByName = new HashMap<>();
        for (Person person : sendable) {
            ssnsByName.computeIfAbsent(person.firstLastKey(), key -> new HashSet<>()).add(person.ssn.digits());
        }
        sendable.stream().filter(person -> ssnsByName.get(person.firstLastKey()).size() > 1)
                .forEach(person -> person.advisories.add(Advisory.DUPLICATE_FIRST_LAST));
    }

    private static <T> Set<T> duplicates(List<T> values) {
        Set<T> seen = new HashSet<>();
        return values.stream().filter(value -> !seen.add(value)).collect(Collectors.toSet());
    }

    static String digits(String raw) {
        return raw == null ? "" : raw.replaceAll("[^0-9]", "");
    }

    /** One row under assessment. The sets fill as rules apply; a person with no holdback is sent. */
    private static final class Person {
        private final W2PersonRow row;
        private final EnumSet<Holdback> holdbacks = EnumSet.noneOf(Holdback.class);
        private final EnumSet<Advisory> advisories = EnumSet.noneOf(Advisory.class);
        private Ssn ssn;
        private LastName last;
        private FirstName first;
        private MiddleName middle;
        private BirthDate birthDate;
        private NameSource nameSource;

        private Person(W2PersonRow row) {
            this.row = row;
        }

        static Person assess(W2PersonRow row, LocalDate today, boolean duplicateEmployeeRow) {
            Person person = new Person(row);
            if (duplicateEmployeeRow) {
                person.holdbacks.add(Holdback.DUPLICATE_EMPLOYEE_ROW);
            }
            person.checkSsn();
            person.checkEin();
            person.checkLastName();
            person.checkFirstAndMiddle();
            person.chooseBirthDate(today);
            if (row.activeW2Count() > 1 && !person.holdbacks.contains(Holdback.W2_FIELDS_DISAGREE)) {
                person.advisories.add(Advisory.SEVERAL_W2S);
            }
            return person;
        }

        /** The SQL nulls a field when the employee's active W-2s disagree on it; one W-2 with a null is a blank. */
        private Holdback whenMissing(Holdback onOneW2) {
            return row.activeW2Count() > 1 ? Holdback.W2_FIELDS_DISAGREE : onOneW2;
        }

        private void checkSsn() {
            String ssnDigits = digits(row.ssn());
            if (ssnDigits.isEmpty()) {
                holdbacks.add(whenMissing(Holdback.NO_SSN));
            } else if (ssnDigits.length() != 9) {
                holdbacks.add(Holdback.SSN_NOT_9_DIGITS);
            } else if (!Ssn.isIssuableShape(ssnDigits)) {
                holdbacks.add(Holdback.SSN_SHAPE_NEVER_ISSUED);
            } else {
                ssn = new Ssn(ssnDigits);
                String hrDigits = digits(row.hrSsnDigits());
                if (!hrDigits.isEmpty() && !hrDigits.equals(ssnDigits)) {
                    advisories.add(Advisory.SSN_DIFFERS_FROM_HR);
                }
            }
        }

        private void checkEin() {
            if (!digits(row.w2BoxBEin()).equals(digits(row.employerEin()))) {
                holdbacks.add(Holdback.W2_EIN_DIFFERS);
            }
        }

        private void checkLastName() {
            if (row.boxELastName() == null) {
                holdbacks.add(whenMissing(Holdback.LAST_NAME_BLANK));
                return;
            }
            String value = absorb(SsaNameRules.normalize(row.boxELastName(), NamePart.LAST));
            last = value == null ? null : new LastName(value);
        }

        private void checkFirstAndMiddle() {
            BoxESplit split = row.boxEFirstMiddle() == null ? null : BoxESplit.of(row);
            if (split == null) {
                holdbacks.add(row.boxEFirstMiddle() == null ? whenMissing(Holdback.FIRST_NAME_BLANK)
                        : Holdback.FIRST_NAME_BLANK);
                return;
            }
            nameSource = split.source();
            if (split.prefixDropped()) {
                advisories.add(Advisory.SUFFIX_OR_PREFIX_DROPPED);
            }
            if (split.source() == NameSource.GUESSED) {
                advisories.add(Advisory.FIRST_MIDDLE_SPLIT_GUESSED);
            }
            String firstValue = absorb(SsaNameRules.normalize(split.first(), NamePart.FIRST));
            first = firstValue == null ? null : new FirstName(firstValue);
            if (split.middle() != null) {
                String middleValue = absorb(SsaNameRules.normalize(split.middle(), NamePart.MIDDLE));
                middle = middleValue == null ? null : new MiddleName(middleValue);
            }
        }

        private String absorb(NormalizedName name) {
            holdbacks.addAll(name.holdbacks());
            advisories.addAll(name.advisories());
            return name.value();
        }

        /** Send a birth date only when it is unambiguous and in SSA's range; a wrong one costs a code 3. */
        private void chooseBirthDate(LocalDate today) {
            LocalDate hr = row.hrBirthDate();
            LocalDate adp = row.adpBirthDate();
            if (hr != null && adp != null && !hr.equals(adp)) {
                advisories.add(Advisory.BIRTH_DATE_OMITTED_MASTERS_DISAGREE);
                return;
            }
            LocalDate chosen = hr != null ? hr : adp;
            if (chosen == null) {
                return;
            }
            birthDate = BirthDate.sendable(chosen, today).orElse(null);
            if (birthDate == null) {
                advisories.add(Advisory.BIRTH_DATE_OMITTED_OUT_OF_RANGE);
            }
        }

        boolean sendable() {
            return holdbacks.isEmpty();
        }

        String firstLastKey() {
            return first.value() + " " + last.value();
        }

        PersonRefs refs() {
            return new PersonRefs(row.employeeId(), row.w2FormIds(), row.companyCodes());
        }

        SentPerson sent() {
            SsnvsRequest request = new SsnvsRequest(ssn, last, first, middle, birthDate,
                    new ControlId(row.employeeId()));
            return new SentPerson(request, refs(), nameSource, advisories);
        }
    }
}
