package com.irs.mef.ssnvs;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Everything the planner decided for one employer EIN. {@code ein} is the digits of employer_ein, which may
 * not be nine digits when {@code gate} is EIN_INVALID. {@code sent} and {@code held} are in employee id order.
 */
record EmployerPlan(String ein, int taxYear, boolean legalCompanyInactive, List<SentPerson> sent,
                    List<HeldPerson> held, Optional<FileGate> gate) {

    /** Which W-2s a person's row stands for. No PII: ids and company codes only. */
    record PersonRefs(long employeeId, String w2FormIds, String companyCodes) {
    }

    record SentPerson(SsnvsRequest request, PersonRefs refs, NameSource nameSource, EnumSet<Advisory> advisories) {

        boolean birthDateSent() {
            return request.birthDate() != null;
        }
    }

    record HeldPerson(PersonRefs refs, EnumSet<Holdback> holdbacks) {
    }

    int rowCount() {
        return sent.size() + held.size();
    }
}
