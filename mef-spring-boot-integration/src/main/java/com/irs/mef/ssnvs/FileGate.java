package com.irs.mef.ssnvs;

import java.util.Optional;

/** Why no request file is written for an employer. SSA takes one EIN per file and at most 250,000 records. */
enum FileGate {
    EIN_INVALID,
    NOTHING_TO_SEND,
    OVER_250000;

    static final int MAX_RECORDS = 250_000;

    static Optional<FileGate> check(String einDigits, int sendableCount) {
        if (!einDigits.matches("[0-9]{9}")) {
            return Optional.of(EIN_INVALID);
        }
        if (sendableCount == 0) {
            return Optional.of(NOTHING_TO_SEND);
        }
        if (sendableCount > MAX_RECORDS) {
            return Optional.of(OVER_250000);
        }
        return Optional.empty();
    }
}
