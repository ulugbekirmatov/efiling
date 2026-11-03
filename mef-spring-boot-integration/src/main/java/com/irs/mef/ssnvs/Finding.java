package com.irs.mef.ssnvs;

/** A reason a person or a results line lands in fixes.csv: what it means and what the operator does about it. */
sealed interface Finding permits Verification, Integrity {

    String name();

    String meaning();

    String action();
}
