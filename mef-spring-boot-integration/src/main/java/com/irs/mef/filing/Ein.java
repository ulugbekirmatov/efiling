package com.irs.mef.filing;


import java.util.regex.Pattern;

/** Employer EIN. Exactly 9 digits, no dash (efileAttachments.xsd EINType). */
public record Ein(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[0-9]{9}$");

    public Ein {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new FilingValidationException("clientEin", "EIN must be exactly 9 digits, no dash");
        }
    }

    /** For log statements — never log the whole EIN. */
    public String masked() {
        return "*****" + value.substring(5);
    }
}
