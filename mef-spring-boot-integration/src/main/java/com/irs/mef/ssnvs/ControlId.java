package com.irs.mef.ssnvs;

import java.util.Locale;

/**
 * User control data (request 90-103, echoed at result 84-97): the employee id, zero-padded to 14 digits.
 * The employee id fits, so no control-to-employee map has to be kept in sync.
 */
record ControlId(long employeeId) {

    static final long MAX = 99_999_999_999_999L;

    ControlId {
        if (employeeId < 1 || employeeId > MAX) {
            throw new IllegalArgumentException("employee id does not fit 14 digits");
        }
    }

    static ControlId parse(String text) {
        if (text == null || !text.matches("[0-9]{14}")) {
            throw new IllegalArgumentException("control data is not 14 digits");
        }
        return new ControlId(Long.parseLong(text));
    }

    String text() {
        return String.format(Locale.ROOT, "%014d", employeeId);
    }
}
