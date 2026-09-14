package com.irs.mef.newsend.domain;

import com.irs.mef.newsend.error.NewSendValidationException;

import java.util.regex.Pattern;

/** Originator EFIN. Exactly 6 digits (efileAttachments.xsd EFINType). */
public record Efin(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[0-9]{6}$");

    public Efin {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new NewSendValidationException("efin", "EFIN must be exactly 6 digits");
        }
    }
}
