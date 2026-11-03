package com.irs.mef.ssnvs;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Optional;

/**
 * A birth date SSA accepts: not before 1800-01-01. The upper bound (not after the build date) depends on
 * when the file is built, so {@link #sendable} owns it; the planner builds birth dates only through it.
 */
record BirthDate(LocalDate date) {

    static final LocalDate EARLIEST = LocalDate.of(1800, 1, 1);
    private static final DateTimeFormatter MMDDYYYY =
            DateTimeFormatter.ofPattern("MMdduuuu").withResolverStyle(ResolverStyle.STRICT);

    BirthDate {
        if (date == null || date.isBefore(EARLIEST)) {
            throw new IllegalArgumentException("birth date is before 1800 or missing");
        }
    }

    static Optional<BirthDate> sendable(LocalDate date, LocalDate asOf) {
        if (date.isBefore(EARLIEST) || date.isAfter(asOf)) {
            return Optional.empty();
        }
        return Optional.of(new BirthDate(date));
    }

    static BirthDate parseMmddyyyy(String text) {
        try {
            return new BirthDate(LocalDate.parse(text, MMDDYYYY));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("birth date is not MMDDYYYY");
        }
    }

    String mmddyyyy() {
        return date.format(MMDDYYYY);
    }

    @Override
    public String toString() {
        return "BirthDate[redacted]";
    }
}
