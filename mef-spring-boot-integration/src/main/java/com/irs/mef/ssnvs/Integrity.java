package com.irs.mef.ssnvs;

/** A results file that does not line up with what was sent. Each one is a fixes.csv row and blocks a clean exit. */
enum Integrity implements Finding {
    UNKNOWN_CONTROL("Results line has control data this build did not send",
            "Check that the results file belongs to this EIN's build; do not act on the line."),
    DUPLICATE_RESULT("SSA returned more than one line for this person",
            "Do not act on either line; re-verify this person."),
    ECHO_MISMATCH("Echoed name, birth date or SSN last four differ from what was sent",
            "The line may not be this person's: do not act on it; re-verify this person."),
    MISSING_FROM_RESULTS("Sent, but no result line came back",
            "Re-verify this person, or wait for the rest of the results."),
    CONFIRMATION_DIFFERS("Results file mixes confirmation numbers",
            "Download each SSA results file separately and reconcile them one at a time.");

    private final String meaning;
    private final String action;

    Integrity(String meaning, String action) {
        this.meaning = meaning;
        this.action = action;
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
