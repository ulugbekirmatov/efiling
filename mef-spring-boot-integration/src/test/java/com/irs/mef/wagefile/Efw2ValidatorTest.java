package com.irs.mef.wagefile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Efw2ValidatorTest {

    @Test
    @DisplayName("a record that is not 512 bytes fails")
    void recordLength() {
        List<String> records = records(writtenFile());
        records.set(2, records.get(2).substring(0, 100));

        assertEquals(List.of("record 3 is 100 bytes"), new Efw2Validator().validate(join(records)));
    }

    @Test
    @DisplayName("an RT wage total that disagrees with its RW rows fails")
    void wageTotal() {
        List<String> records = records(writtenFile());
        FixedWidth total = FixedWidth.parse(records.get(3));
        total.put(10, 24, FixedWidth.zeroFill(1, 15));
        records.set(3, total.text());

        assertEquals(
                List.of("RE 003000004 wages RT 1 cents, RW 250000 cents"),
                new Efw2Validator().validate(join(records)));
    }

    @Test
    @DisplayName("the writer output is a valid file")
    void acceptsWriterOutput() {
        assertEquals(List.of(), new Efw2Validator().validate(writtenFile()));
    }

    private static String writtenFile() {
        return new Efw2Writer().write(WageFileSamples.oneEmployee(250000, 0, 0, 0, 0, 0));
    }

    private static List<String> records(String file) {
        return new ArrayList<>(Arrays.asList(file.split("\r\n")));
    }

    private static String join(List<String> records) {
        return String.join("\r\n", records) + "\r\n";
    }
}
