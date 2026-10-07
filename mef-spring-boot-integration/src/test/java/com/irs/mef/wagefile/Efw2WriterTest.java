package com.irs.mef.wagefile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
