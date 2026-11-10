package com.irs.mef.ssnvs;

/** 1-10 letters A-Z, already normalized by {@link SsaNameRules}. */
record FirstName(String value) {

    FirstName {
        if (!NamePart.FIRST.accepts(value)) {
            throw new IllegalArgumentException("first name must be 1-10 letters A-Z");
        }
    }

    @Override
    public String toString() {
        return "FirstName[redacted]";
    }
}
