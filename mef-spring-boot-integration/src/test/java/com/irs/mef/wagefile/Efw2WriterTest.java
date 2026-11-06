package com.irs.mef.wagefile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Efw2WriterTest {

    @Test
    @DisplayName("one employer, one employee, wages 250000 cents")
    void oneEmployerOneEmployee() {
        String file = new Efw2Writer().write(WageFileSamples.oneEmployee(250000, 0, 0, 0, 0, 0));

        String[] records = file.split("\r\n");
        assertEquals(String.join("\r\n", records) + "\r\n", file);
        assertEquals(5, records.length);
        for (String record : records) {
            assertEquals(512, record.getBytes(StandardCharsets.US_ASCII).length);
        }
        assertEquals("RA", records[0].substring(0, 2));
        assertEquals("RE", records[1].substring(0, 2));
        assertEquals("RW", records[2].substring(0, 2));
        assertEquals("RT", records[3].substring(0, 2));
        assertEquals("RF", records[4].substring(0, 2));
        assertEquals("00000250000", FixedWidth.parse(records[2]).get(188, 198));
        assertEquals("00000000000", FixedWidth.parse(records[2]).get(199, 209));
        assertEquals("00000000000", FixedWidth.parse(records[2]).get(210, 220));
        assertEquals("00000000000", FixedWidth.parse(records[2]).get(221, 231));
        assertEquals("00000000000", FixedWidth.parse(records[2]).get(232, 242));
        assertEquals("00000000000", FixedWidth.parse(records[2]).get(243, 253));
        assertEquals("Pyramos@Example.com                     ", FixedWidth.parse(records[0]).get(446, 485));
    }

    @Test
    @DisplayName("tips, Box 12 and retirement plan land in RW and total in RT")
    void otherAmountsAndRetirementPlan() {
        EmployeeWage employee = new EmployeeWage("123456789", "Test", "", "Person", "1 Main St", "Austin", "TX",
                "78701", 6_000_000L, 0L, 5_000_000L, 400_000L, 6_000_000L, 87_000L,
                Map.of(Efw2Amount.SOCIAL_SECURITY_TIPS, 1_000_000L,
                        Efw2Amount.DEFERRED_COMPENSATION_401K, 500_000L,
                        Efw2Amount.EMPLOYER_SPONSORED_HEALTH_COVERAGE, 840_000L),
                true);
        WageYear sample = WageFileSamples.oneEmployee(1, 0, 0, 0, 0, 0);
        EmployerWage employer = sample.employers().get(0);
        WageYear year = new WageYear(sample.submitter(), List.of(new EmployerWage(employer.taxYear(), employer.ein(),
                employer.name(), employer.deliveryAddress(), employer.city(), employer.state(), employer.zip(),
                employer.contactName(), employer.contactPhone(), employer.contactEmail(), List.of(employee))));

        String[] records = new Efw2Writer().write(year).split("\r\n");

        FixedWidth rw = FixedWidth.parse(records[2]);
        assertEquals("00001000000", rw.get(254, 264));
        assertEquals("00000500000", rw.get(287, 297));
        assertEquals("00000840000", rw.get(463, 473));
        assertEquals("1", rw.get(488, 488));
        FixedWidth rt = FixedWidth.parse(records[3]);
        assertEquals("000000001000000", rt.get(100, 114));
        assertEquals("000000000500000", rt.get(145, 159));
        assertEquals("000000000840000", rt.get(295, 309));
        assertEquals(List.of(), new Efw2Validator().validate(String.join("\r\n", records) + "\r\n"));
    }

    @Test
    @DisplayName("RA repeats the company address as the submitter address and carries the contact")
    void submitterContactFields() {
        String[] records = new Efw2Writer().write(WageFileSamples.oneEmployee(250000, 0, 0, 0, 0, 0)).split("\r\n");

        FixedWidth ra = FixedWidth.parse(records[0]);
        assertEquals("1 MAIN ST             ", ra.get(296, 317));
        assertEquals("AUSTIN                ", ra.get(318, 339));
        assertEquals("TX", ra.get(340, 341));
        assertEquals("78701", ra.get(342, 346));
        assertEquals("PAYROLL CONTACT            ", ra.get(396, 422));
        assertEquals("5125550100     ", ra.get(423, 437));
    }

    @Test
    @DisplayName("a null other amount is rejected as bad input")
    void nullOtherAmountIsRejected() {
        Map<Efw2Amount, Long> amounts = new HashMap<>();
        amounts.put(Efw2Amount.SOCIAL_SECURITY_TIPS, null);

        assertThrows(IllegalArgumentException.class, () -> new EmployeeWage("123456789", "Test", "", "Person",
                "1 Main St", "Austin", "TX", "78701", 0L, 0L, 0L, 0L, 0L, 0L, amounts, false));
    }
}
