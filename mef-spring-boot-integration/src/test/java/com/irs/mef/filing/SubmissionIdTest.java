package com.irs.mef.filing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Offline. Pins the id invariants everything else assumes (MEF00004 guard included). */
class SubmissionIdTest {

    private static final Efin EFIN = new Efin("238689");
    private static final Originator ORIGINATOR = new Originator(
            EFIN,
            new ReportingAgentPin("00000"),
            SoftwareIds.of(SchemaRelease.Y2026Q1_V4, new SoftwareId("00000000")),
            "192.0.2.1",
            new ReportingAgentIdentity(
                    new Ein("000000000"),
                    "PYRAMOS SOFTWARE LLC",
                    new NameControl("PYRA"),
                    new UsAddress("100 Market Street", "Philadelphia", "PA", "19103")));
    private static final ZoneId EASTERN = Efile.PROCESSING_ZONE;
    private static final SecureRandom RNG = new SecureRandom();

    @Test
    @DisplayName("generate produces exactly [0-9]{13}[a-z0-9]{7}")
    void generateMatchesIrsPattern() {
        SubmissionId id = SubmissionId.mint(ORIGINATOR, Instant.now(), EASTERN, RNG);

        assertEquals(20, id.value().length());
        assertTrue(id.value().matches("[0-9]{13}[a-z0-9]{7}"), "got: " + id.value());
    }

    @Test
    @DisplayName("the date component is yyyyDDD of the CURRENT processing date, not a tax-period year")
    void dateComponentIsProcessingDate() {
        // 2026-03-31 UTC-midnightish is still 2026-03-30 in Eastern — the zone matters
        Instant now = Instant.parse("2026-03-31T02:00:00Z");
        LocalDate expected = now.atZone(EASTERN).toLocalDate();

        SubmissionId id = SubmissionId.mint(ORIGINATOR, now, EASTERN, RNG);

        assertEquals(expected, id.processingDate());
        String expectedDate = String.format("%04d%03d", expected.getYear(), expected.getDayOfYear());
        assertEquals(expectedDate, id.value().substring(6, 13));
    }

    @Test
    @DisplayName("the manifest EFIN is derived from the id and equals its first 6 chars")
    void efinIsDerivedFromTheId() {
        SubmissionId id = SubmissionId.mint(ORIGINATOR, Instant.now(), EASTERN, RNG);

        assertEquals(EFIN, id.efin());
        assertEquals(id.value().substring(0, 6), id.efin().value());
    }

    @Test
    @DisplayName("day-of-year is zero-padded to 3 digits in January")
    void dayOfYearIsZeroPadded() {
        Instant january5 = Instant.parse("2026-01-05T15:00:00Z");

        SubmissionId id = SubmissionId.mint(ORIGINATOR, january5, EASTERN, RNG);

        assertEquals("2026005", id.value().substring(6, 13));
    }

    @Test
    @DisplayName("malformed ids are unrepresentable")
    void malformedIdsRejected() {
        assertThrows(FilingValidationException.class, () -> new SubmissionId("too-short"));
        assertThrows(FilingValidationException.class,
                () -> new SubmissionId("2386892026090ABCDEFG")); // uppercase suffix
        assertThrows(FilingValidationException.class, () -> new SubmissionId(null));
    }
}
