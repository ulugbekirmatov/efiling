package com.irs.mef.filing;


import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * IRS submission id. Exactly 20 chars: {@code [0-9]{13}[a-z0-9]{7}}
 * = EFIN(6) + processing date yyyyDDD(7) + 7-char lowercase base36 suffix.
 *
 * INVARIANT, encoded rather than asserted: the manifest EFIN is derived from this id via
 * {@link #efin()}. There is no second place to get it, so the schema rule
 * "manifest EFIN == first 6 chars of SubmissionId" cannot be violated.
 *
 * The date component is the CURRENT PROCESSING date, never the tax-period year
 * (IRS rejects with MEF00004 otherwise). See SUBMISSION_ID_FORMAT.md.
 */
public record SubmissionId(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[0-9]{13}[a-z0-9]{7}$");
    private static final String SUFFIX_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SUFFIX_LENGTH = 7;

    public SubmissionId {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new FilingValidationException("submissionId",
                    "Submission id must match [0-9]{13}[a-z0-9]{7}");
        }
    }

    static SubmissionId mint(Originator originator, Instant now, ZoneId processingZone, RandomGenerator random) {
        LocalDate processingDate = now.atZone(processingZone).toLocalDate();
        String date = String.format("%04d%03d", processingDate.getYear(), processingDate.getDayOfYear());
        StringBuilder suffix = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            suffix.append(SUFFIX_ALPHABET.charAt(random.nextInt(SUFFIX_ALPHABET.length())));
        }
        return new SubmissionId(originator.efin().value() + date + suffix);
    }

    /** The originator EFIN embedded in the id — the single source the manifest reads from. */
    Efin efin() {
        return new Efin(value.substring(0, 6));
    }

    /** The yyyyDDD processing date embedded in the id. */
    LocalDate processingDate() {
        int year = Integer.parseInt(value.substring(6, 10));
        int dayOfYear = Integer.parseInt(value.substring(10, 13));
        return LocalDate.ofYearDay(year, dayOfYear);
    }
}
