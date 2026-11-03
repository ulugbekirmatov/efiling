package com.irs.mef.wagefile;

import java.util.List;

/** One EFW2 file: the submitter and every employer for a single tax year. */
public record WageYear(Submitter submitter, List<EmployerWage> employers) {

    public WageYear {
        if (submitter == null) {
            throw new IllegalArgumentException("submitter is required");
        }
        if (employers == null || employers.isEmpty()) {
            throw new IllegalArgumentException("at least one employer is required");
        }
        employers = List.copyOf(employers);
        int taxYear = employers.get(0).taxYear();
        for (EmployerWage employer : employers) {
            if (employer.taxYear() != taxYear) {
                throw new IllegalArgumentException("every employer must use tax year " + taxYear);
            }
        }
    }

    public int taxYear() {
        return employers.get(0).taxYear();
    }
}
