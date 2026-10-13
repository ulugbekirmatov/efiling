package com.irs.mef.ssnvs;

/** Something the operator should know about a person who is written. Written to notes.csv, one row per code. */
enum Advisory {
    SSN_DIFFERS_FROM_HR,
    FIRST_MIDDLE_SPLIT_GUESSED,
    SUFFIX_OR_PREFIX_DROPPED,
    NAME_TRUNCATED,
    DUPLICATE_FIRST_LAST,
    BIRTH_DATE_OMITTED_MASTERS_DISAGREE,
    BIRTH_DATE_OMITTED_OUT_OF_RANGE,
    SEVERAL_W2S
}
