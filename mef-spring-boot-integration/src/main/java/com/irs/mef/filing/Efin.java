package com.irs.mef.filing;


import java.util.regex.Pattern;

/** Originator EFIN. Exactly 6 digits (efileAttachments.xsd EFINType). */
record Efin(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[0-9]{6}$");

    Efin {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new FilingValidationException("efin", "EFIN must be exactly 6 digits");
        }
    }
}
