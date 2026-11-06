package com.irs.mef.wagefile.accuwage;

/** One file to upload to AccuWage Online, what it exercises, and the result AccuWage should report. */
record AccuWageScenario(String fileName, String purpose, AccuWageOutcome expected, String expectedResult,
                        String fileText) {

    enum AccuWageOutcome {
        NO_ERRORS,
        ERRORS
    }
}
