package com.irs.mef.ssnvs;

import com.irs.mef.ssnvs.EmployerPlan.SentPerson;

import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

/**
 * manifest.json: what a request record cannot carry, keyed by control data. No SSN, name or birth date,
 * only whether a birth date was sent.
 */
record Manifest(String ein, int taxYear, String builtOn, int recordCount, Map<String, Person> people) {

    record Person(long employeeId, String w2FormIds, String companyCodes, NameSource nameSource,
                  boolean birthDateSent) {
    }

    static Manifest of(EmployerPlan plan, LocalDate builtOn) {
        Map<String, Person> people = new TreeMap<>();
        for (SentPerson sent : plan.sent()) {
            people.put(sent.request().control().text(), new Person(sent.refs().employeeId(),
                    sent.refs().w2FormIds(), sent.refs().companyCodes(), sent.nameSource(), sent.birthDateSent()));
        }
        return new Manifest(plan.ein(), plan.taxYear(), builtOn.toString(), plan.sent().size(), people);
    }
}
