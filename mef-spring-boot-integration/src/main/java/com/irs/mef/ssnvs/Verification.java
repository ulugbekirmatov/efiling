package com.irs.mef.ssnvs;

import java.util.Arrays;

/**
 * SSA's verification code at result position 98 (SSNVS Handbook pp.20-21), with the operator's action.
 * Any other character, including the pre-2011 gender codes 2 and 4, is UNKNOWN and never counts as verified.
 */
enum Verification implements Finding {
    VERIFIED(' ', "SSN and name verified", ""),
    SSN_NOT_IN_FILE('1', "SSN not in SSA's file (never issued)",
            "Check the SSN against the Social Security card, fix the employee master and every W-2 listed, then re-verify."),
    BIRTH_DATE_MISMATCH('3', "Name and SSN match; birth date does not",
            "Name and SSN are fine, so the W-2 is OK. Fix the birth date in HR/ADP."),
    NAME_MISMATCH('5', "Name does not match the SSN; birth date not checked",
            "Compare box e with the Social Security card. If name_source is GUESSED, confirm the first/middle split first. "
                    + "The employee may need to update their name with SSA. A mismatch is not grounds for adverse action."),
    NOT_VERIFIED_OTHER('6', "SSN did not verify for another reason",
            "Ask the employee to contact SSA, then re-verify."),
    UNKNOWN('?', "Code SSA documents no meaning for; treated as not verified",
            "Hold the W-2 and ask BSO support what the code means.");

    private final char code;
    private final String meaning;
    private final String action;

    Verification(char code, String meaning, String action) {
        this.code = code;
        this.meaning = meaning;
        this.action = action;
    }

    static Verification of(char code) {
        return Arrays.stream(values()).filter(value -> value != UNKNOWN && value.code == code).findFirst()
                .orElse(UNKNOWN);
    }

    @Override
    public String meaning() {
        return meaning;
    }

    @Override
    public String action() {
        return action;
    }
}
