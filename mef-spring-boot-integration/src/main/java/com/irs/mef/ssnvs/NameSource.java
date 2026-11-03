package com.irs.mef.ssnvs;

/** Where the cut between first and middle name in box e came from. Recorded per person in the manifest. */
enum NameSource {
    HR_FULL,
    HR_INITIAL,
    ADP_FULL,
    SINGLE_TOKEN,
    HR_FIRST_PREFIX,
    GUESSED
}
