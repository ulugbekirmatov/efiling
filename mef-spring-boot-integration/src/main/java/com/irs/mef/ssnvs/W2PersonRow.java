package com.irs.mef.ssnvs;

import java.time.LocalDate;

/**
 * One row of sql/ssnvs_w2_people.sql as exported from DBeaver: one employee under one legal company.
 * Identifiers and dates are parsed; names, SSNs and EINs stay raw because judging them is planner policy.
 * Blank cells are null. {@code toString} shows only the line and employee id.
 */
record W2PersonRow(
        int line,
        String legalCompanyId,
        String employerEin,
        Boolean legalCompanyIsActive,
        int taxYear,
        long employeeId,
        int activeW2Count,
        String w2FormIds,
        String companyCodes,
        String w2BoxBEin,
        String ssn,
        String hrSsnDigits,
        String boxEFirstMiddle,
        String boxELastName,
        String hrFirstName,
        String hrMiddleName,
        String hrMiddleInitial,
        String hrLastName,
        String adpFirstName,
        String adpMiddleName,
        String adpLastName,
        LocalDate hrBirthDate,
        LocalDate adpBirthDate) {

    @Override
    public String toString() {
        return "W2PersonRow[line=" + line + ", employeeId=" + employeeId + "]";
    }
}
