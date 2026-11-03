package com.irs.mef.ssnvs;

/** 1-7 letters A-Z. No middle name is an absent {@code MiddleName}, never an empty one. */
record MiddleName(String value) {

    MiddleName {
        if (!NamePart.MIDDLE.accepts(value)) {
            throw new IllegalArgumentException("middle name must be 1-7 letters A-Z");
        }
    }

    @Override
    public String toString() {
        return "MiddleName[redacted]";
    }
}
