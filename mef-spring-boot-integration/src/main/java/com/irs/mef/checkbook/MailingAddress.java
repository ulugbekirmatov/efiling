package com.irs.mef.checkbook;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** US recipient address of a mailed check; Checkbook's optional country field is omitted. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MailingAddress(
        @JsonProperty("line_1") String line1,
        @JsonProperty("line_2") String line2,
        String city,
        String state,
        String zip) {

    static final int MAX_LINE_LENGTH = 100;
    static final int MAX_CITY_LENGTH = 40;
    static final int MAX_STATE_LENGTH = 30;
    static final int MAX_ZIP_LENGTH = 10;

    public MailingAddress {
        requireWithin("line_1", line1, MAX_LINE_LENGTH);
        requireWithin("city", city, MAX_CITY_LENGTH);
        requireWithin("state", state, MAX_STATE_LENGTH);
        requireWithin("zip", zip, MAX_ZIP_LENGTH);
        if (line2 != null && line2.length() > MAX_LINE_LENGTH) {
            throw new IllegalArgumentException("line_2 must be at most " + MAX_LINE_LENGTH + " characters");
        }
    }

    private static void requireWithin(String field, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
        }
    }
}
