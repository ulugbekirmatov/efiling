package com.irs.mef.wagefile;

import java.util.Locale;

final class FieldRules {

    private FieldRules() {
    }

    static String digits(String value, int width, String field) {
        if (value == null || !value.matches("[0-9]{" + width + "}")) {
            throw new IllegalArgumentException(field + " must be " + width + " digits");
        }
        return value;
    }

    static String required(String value, int max, String field) {
        String bounded = bounded(value, max, field);
        if (bounded.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return bounded;
    }

    static String bounded(String value, int max, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        ascii(value, field);
        if (value.length() > max) {
            throw new IllegalArgumentException(field + " exceeds " + max + " characters");
        }
        return value;
    }

    static String state(String value) {
        if (value == null) {
            throw new IllegalArgumentException("state is required");
        }
        String upper = value.toUpperCase(Locale.ROOT);
        if (!upper.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("state must be 2 letters");
        }
        return upper;
    }

    static String email(String value) {
        String bounded = required(value, 40, "contact email");
        return bounded;
    }

    static String phone(String value) {
        String digits = value == null ? "" : value.replaceAll("\\D", "");
        if (digits.length() > 15) {
            throw new IllegalArgumentException("contact phone exceeds 15 digits");
        }
        return digits;
    }

    static void nonNegative(long cents, String field) {
        if (cents < 0) {
            throw new IllegalArgumentException(field + " cannot be negative");
        }
    }

    static void ascii(String value, String field) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\r' || c == '\n' || c > 127) {
                throw new IllegalArgumentException(field + " must be ASCII");
            }
        }
    }
}
