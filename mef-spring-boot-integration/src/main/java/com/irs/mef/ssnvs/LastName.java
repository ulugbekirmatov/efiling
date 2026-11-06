package com.irs.mef.ssnvs;

/** 1-13 letters A-Z, already normalized by {@link SsaNameRules}. */
record LastName(String value) {

    LastName {
        if (!NamePart.LAST.accepts(value)) {
            throw new IllegalArgumentException("last name must be 1-13 letters A-Z");
        }
    }

    @Override
    public String toString() {
        return "LastName[redacted]";
    }
}
