package com.irs.mef.ssnvs;

import java.util.Arrays;

/**
 * One 130-character SSNVS request record (SSNVS Handbook, January 2025, pp.18-19). Built only from value
 * types, so an un-normalized or blank required name cannot reach the file. {@code middle} and
 * {@code birthDate} are null when absent.
 */
record SsnvsRequest(Ssn ssn, LastName last, FirstName first, MiddleName middle, BirthDate birthDate,
                    ControlId control) {

    static final int RECORD_LENGTH = 130;
    static final String ENTRY_CODE = "TPV";
    static final String PROCESSING_CODE = "214";
    static final String REQUESTER_ID = "OEVS";
    static final String MULTIPLE_REQUEST = "000";

    /** Request layout, 1-based inclusive positions. Unlisted positions are blanks reserved for SSA. */
    enum Field {
        SSN(1, 9),
        ENTRY_CODE(10, 12),
        PROCESSING_CODE(13, 15),
        LAST_NAME(16, 28),
        FIRST_NAME(29, 38),
        MIDDLE_NAME(39, 45),
        BIRTH_DATE(46, 53),
        RESERVED_54_89(54, 89),
        CONTROL(90, 103),
        RESERVED_104_123(104, 123),
        REQUESTER_ID(124, 127),
        MULTIPLE_REQUEST(128, 130);

        final int start;
        final int end;

        Field(int start, int end) {
            this.start = start;
            this.end = end;
        }

        int width() {
            return end - start + 1;
        }

        String in(String record) {
            return record.substring(start - 1, end);
        }
    }

    SsnvsRequest {
        if (ssn == null || last == null || first == null || control == null) {
            throw new IllegalArgumentException("SSN, last name, first name and control are required");
        }
    }

    String record() {
        char[] line = new char[RECORD_LENGTH];
        Arrays.fill(line, ' ');
        put(line, Field.SSN, ssn.digits());
        put(line, Field.ENTRY_CODE, ENTRY_CODE);
        put(line, Field.PROCESSING_CODE, PROCESSING_CODE);
        put(line, Field.LAST_NAME, last.value());
        put(line, Field.FIRST_NAME, first.value());
        put(line, Field.MIDDLE_NAME, middle == null ? "" : middle.value());
        put(line, Field.BIRTH_DATE, birthDate == null ? "" : birthDate.mmddyyyy());
        put(line, Field.CONTROL, control.text());
        put(line, Field.REQUESTER_ID, REQUESTER_ID);
        put(line, Field.MULTIPLE_REQUEST, MULTIPLE_REQUEST);
        String record = new String(line);
        if (record.length() != RECORD_LENGTH) {
            throw new IllegalStateException("SSNVS request record is not 130 characters");
        }
        return record;
    }

    /** Reads back a record this package wrote. Throws IllegalArgumentException with fixed text, never a value. */
    static SsnvsRequest parse(String record) {
        if (record.length() != RECORD_LENGTH
                || !ENTRY_CODE.equals(Field.ENTRY_CODE.in(record))
                || !PROCESSING_CODE.equals(Field.PROCESSING_CODE.in(record))
                || !REQUESTER_ID.equals(Field.REQUESTER_ID.in(record))
                || !MULTIPLE_REQUEST.equals(Field.MULTIPLE_REQUEST.in(record))
                || !Field.RESERVED_54_89.in(record).isBlank()
                || !Field.RESERVED_104_123.in(record).isBlank()) {
            throw new IllegalArgumentException("not an SSNVS request record");
        }
        String middle = Field.MIDDLE_NAME.in(record).stripTrailing();
        String birthDate = Field.BIRTH_DATE.in(record);
        return new SsnvsRequest(
                new Ssn(Field.SSN.in(record)),
                new LastName(Field.LAST_NAME.in(record).stripTrailing()),
                new FirstName(Field.FIRST_NAME.in(record).stripTrailing()),
                middle.isEmpty() ? null : new MiddleName(middle),
                birthDate.isBlank() ? null : BirthDate.parseMmddyyyy(birthDate),
                ControlId.parse(Field.CONTROL.in(record)));
    }

    private static void put(char[] line, Field field, String value) {
        if (value.length() > field.width()) {
            throw new IllegalArgumentException("value is wider than field " + field);
        }
        value.getChars(0, value.length(), line, field.start - 1);
    }

    @Override
    public String toString() {
        return "SsnvsRequest[control=" + control.text() + "]";
    }
}
