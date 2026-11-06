package com.irs.mef.wagefile;

import java.util.Arrays;
import java.util.Locale;

/**
 * One 512-byte EFW2 record. Positions are 1-based and inclusive.
 * {@code put} accepts only a value that is already the field width.
 */
public final class FixedWidth {

    public static final int RECORD_LENGTH = 512;

    private final char[] data = new char[RECORD_LENGTH];

    public FixedWidth() {
        Arrays.fill(data, ' ');
    }

    public static FixedWidth parse(String record) {
        if (record == null || record.getBytes(java.nio.charset.StandardCharsets.UTF_8).length != RECORD_LENGTH
                || record.length() != RECORD_LENGTH) {
            int bytes = record == null ? 0 : record.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            throw new IllegalArgumentException("record is " + bytes + " bytes");
        }
        FixedWidth recordWidth = new FixedWidth();
        record.getChars(0, RECORD_LENGTH, recordWidth.data, 0);
        return recordWidth;
    }

    public void put(int start, int end, String value) {
        int width = width(start, end);
        if (value == null || value.length() != width || breaksTheRecord(value)) {
            int actual = value == null ? 0 : value.length();
            throw new IllegalArgumentException(
                    "value length " + actual + " does not fit field " + start + "-" + end + " (" + width + ")");
        }
        value.getChars(0, width, data, start - 1);
    }

    public String get(int start, int end) {
        int width = width(start, end);
        return new String(data, start - 1, width);
    }

    public String text() {
        return new String(data);
    }

    public static String left(String value, int width) {
        return pad(value, width, true);
    }

    /** Email fields keep the address's own case. */
    public static String leftRaw(String value, int width) {
        return pad(value, width, false);
    }

    public static String zeroFill(long value, int width) {
        if (value < 0) {
            throw new IllegalArgumentException("negative amount " + value);
        }
        String digits = Long.toString(value);
        if (digits.length() > width) {
            throw new IllegalArgumentException("amount " + value + " does not fit " + width + " digits");
        }
        return "0".repeat(width - digits.length()) + digits;
    }

    private static String pad(String value, int width, boolean uppercase) {
        String text = value == null ? "" : value;
        if (uppercase) {
            text = text.toUpperCase(Locale.ROOT);
        }
        if (text.length() > width || breaksTheRecord(text)) {
            throw new IllegalArgumentException("value length " + text.length() + " exceeds " + width);
        }
        return text + " ".repeat(width - text.length());
    }

    private static int width(int start, int end) {
        if (start < 1 || end > RECORD_LENGTH || start > end) {
            throw new IllegalArgumentException("field " + start + "-" + end + " is outside the 512-byte record");
        }
        return end - start + 1;
    }

    private static boolean breaksTheRecord(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\r' || c == '\n' || c > 127) {
                return true;
            }
        }
        return false;
    }
}
