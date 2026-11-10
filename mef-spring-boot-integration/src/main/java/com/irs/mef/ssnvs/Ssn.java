package com.irs.mef.ssnvs;

/**
 * Nine digits SSA could have issued: area not 000, 666 or 900-999, group not 00, serial not 0000.
 * {@code toString} is redacted so a log line or an assertion message never carries the number.
 */
record Ssn(String digits) {

    private static final String REDACTED = "Ssn[redacted]";

    Ssn {
        if (!isIssuableShape(digits)) {
            throw new IllegalArgumentException("SSN is not nine digits of an issuable shape");
        }
    }

    static boolean isIssuableShape(String digits) {
        if (digits == null || !digits.matches("[0-9]{9}")) {
            return false;
        }
        int area = Integer.parseInt(digits.substring(0, 3));
        boolean areaIssuable = area != 0 && area != 666 && area < 900;
        return areaIssuable && !digits.startsWith("00", 3) && !digits.startsWith("0000", 5);
    }

    /** Result positions 6-9 are never masked, so reconciliation compares these four. */
    String lastFour() {
        return digits.substring(5);
    }

    @Override
    public String toString() {
        return REDACTED;
    }
}
