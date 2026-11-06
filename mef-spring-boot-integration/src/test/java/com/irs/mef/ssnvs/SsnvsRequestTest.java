package com.irs.mef.ssnvs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SsnvsRequestTest {

    private static final int PERSON = 7;

    private static SsnvsRequest knownPerson() {
        return new SsnvsRequest(new Ssn(SsnvsFixtures.ssn(PERSON)), new LastName(SsnvsFixtures.last(PERSON)),
                new FirstName(SsnvsFixtures.first(PERSON)), new MiddleName(SsnvsFixtures.middle(PERSON)),
                new BirthDate(LocalDate.of(1971, 3, 9)), new ControlId(SsnvsFixtures.employeeId(PERSON)));
    }

    @Test
    @DisplayName("a known synthetic person renders the exact 130-character record, field by field")
    void exactRecord() {
        String record = knownPerson().record();

        assertEquals(130, record.getBytes(StandardCharsets.US_ASCII).length);
        assertEquals(SsnvsFixtures.ssn(PERSON), record.substring(0, 9));
        assertEquals("TPV", record.substring(9, 12));
        assertEquals("214", record.substring(12, 15));
        assertEquals("ZQLAAH       ", record.substring(15, 28));
        assertEquals("ZQFAAH    ", record.substring(28, 38));
        assertEquals("ZQMAAH ", record.substring(38, 45));
        assertEquals("03091971", record.substring(45, 53));
        assertEquals(" ".repeat(36), record.substring(53, 89));
        assertEquals("00000000004107", record.substring(89, 103));
        assertEquals(" ".repeat(20), record.substring(103, 123));
        assertEquals("OEVS", record.substring(123, 127));
        assertEquals("000", record.substring(127, 130));
    }

    @Test
    @DisplayName("no middle name and no birth date leave positions 39-53 blank")
    void optionalFieldsBlank() {
        SsnvsRequest request = new SsnvsRequest(new Ssn(SsnvsFixtures.ssn(1)), new LastName("ZQL"),
                new FirstName("ZQF"), null, null, new ControlId(1));

        assertEquals(" ".repeat(15), request.record().substring(38, 53));
    }

    @Test
    @DisplayName("the layout table covers positions 1 to 130 once, in order")
    void layoutCoversEveryPosition() {
        int next = 1;
        for (SsnvsRequest.Field field : SsnvsRequest.Field.values()) {
            assertEquals(next, field.start, field.name());
            next = field.end + 1;
        }
        assertEquals(131, next);
    }

    @Test
    @DisplayName("a written record parses back to the same record")
    void roundTrip() {
        String record = knownPerson().record();

        assertEquals(record, SsnvsRequest.parse(record).record());
        assertNull(SsnvsRequest.parse(new SsnvsRequest(new Ssn(SsnvsFixtures.ssn(1)), new LastName("ZQL"),
                new FirstName("ZQF"), null, null, new ControlId(1)).record()).middle());
    }

    @Test
    @DisplayName("SSNs SSA never issues are unconstructible")
    void ssnShapes() {
        List<String> neverIssued = List.of(
                String.format("%03d%02d%04d", 0, 12, 3456),
                String.format("%03d%02d%04d", 666, 12, 3456),
                String.format("%03d%02d%04d", 912, 12, 3456),
                String.format("%03d%02d%04d", 412, 0, 3456),
                String.format("%03d%02d%04d", 412, 12, 0),
                "41212345",
                "41212345X");
        for (String digits : neverIssued) {
            assertThrows(IllegalArgumentException.class, () -> new Ssn(digits), "shape " + neverIssued.indexOf(digits));
        }
        assertTrue(Ssn.isIssuableShape(SsnvsFixtures.ssn(0)));
    }

    @Test
    @DisplayName("names outside A-Z or wider than the field are unconstructible")
    void nameTypes() {
        assertThrows(IllegalArgumentException.class, () -> new LastName("ZQ-L"));
        assertThrows(IllegalArgumentException.class, () -> new LastName("Z".repeat(14)));
        assertThrows(IllegalArgumentException.class, () -> new FirstName(""));
        assertThrows(IllegalArgumentException.class, () -> new FirstName("zqf"));
        assertThrows(IllegalArgumentException.class, () -> new MiddleName(""));
        assertThrows(IllegalArgumentException.class, () -> new MiddleName("Z".repeat(8)));
        assertThrows(IllegalArgumentException.class, () -> new BirthDate(LocalDate.of(1799, 12, 31)));
        assertThrows(IllegalArgumentException.class, () -> new ControlId(0));
    }

    @Test
    @DisplayName("value types and the request redact their values in toString and exception messages")
    void redaction() {
        SsnvsRequest request = knownPerson();
        String shown = request + " " + request.ssn() + " " + request.last() + " " + request.first() + " "
                + request.middle() + " " + request.birthDate();

        assertFalse(shown.contains(SsnvsFixtures.ssn(PERSON)));
        assertFalse(shown.contains(SsnvsFixtures.last(PERSON)));
        assertFalse(shown.contains("1971"));
        String message = assertThrows(IllegalArgumentException.class,
                () -> new Ssn(String.format("%03d%02d%04d", 666, 12, 3456))).getMessage();
        assertFalse(message.contains("666"));
    }
}
