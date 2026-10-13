package com.irs.mef.ssnvs;

/** Why a person is not written to the request file. Written to holdbacks.csv, one row per code. */
enum Holdback {
    NO_SSN,
    SSN_NOT_9_DIGITS,
    SSN_SHAPE_NEVER_ISSUED,
    W2_FIELDS_BLANK_OR_DISAGREE,
    W2_EIN_DIFFERS,
    W2_EIN_BLANK,
    LAST_NAME_BLANK,
    FIRST_NAME_BLANK,
    NAME_INVALID_CHARACTER,
    DUPLICATE_SSN,
    DUPLICATE_EMPLOYEE_ROW
}
