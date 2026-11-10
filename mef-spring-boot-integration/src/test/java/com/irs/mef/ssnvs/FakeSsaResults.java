package com.irs.mef.ssnvs;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Plays SSA for tests: turns a sent ssnvs.txt into result lines (SSNVS Handbook pp.20-21) with chosen codes.
 * Like SSA, it masks the first five SSN digits for codes blank, 3 and 6, and does not echo TPV.
 */
final class FakeSsaResults {

    static final String CONFIRMATION = "ZQCONFIRM0000001";
    private static final Set<Character> MASKED_CODES = Set.of(' ', '3', '6');

    private FakeSsaResults() {
    }

    /** Everyone not in {@code codes} verifies; everyone not in {@code deaths} is alive. */
    static String answer(byte[] requestFile, Map<Long, Character> codes, Map<Long, Character> deaths) {
        return Arrays.stream(new String(requestFile, StandardCharsets.US_ASCII).split("\r\n"))
                .map(request -> {
                    long employeeId = Long.parseLong(request.substring(89, 103));
                    return line(request, codes.getOrDefault(employeeId, ' '), deaths.getOrDefault(employeeId, 'N'),
                            CONFIRMATION) + "\r\n";
                })
                .collect(Collectors.joining());
    }

    static String answer(byte[] requestFile, Map<Long, Character> codes) {
        return answer(requestFile, codes, Map.of());
    }

    static String line(String request, char code, char death, String confirmation) {
        char[] line = new char[130];
        Arrays.fill(line, ' ');
        String ssn = request.substring(0, 9);
        put(line, 1, MASKED_CODES.contains(code) ? "XXXXX" + ssn.substring(5) : ssn);
        put(line, 10, request.substring(15, 28));
        put(line, 23, request.substring(28, 38));
        put(line, 33, request.substring(38, 45));
        put(line, 40, request.substring(45, 53));
        put(line, 49, confirmation);
        put(line, 84, request.substring(89, 103));
        put(line, 98, String.valueOf(code));
        put(line, 99, "214");
        put(line, 102, "OEVS");
        put(line, 106, "000");
        put(line, 121, String.valueOf(death));
        return new String(line);
    }

    private static void put(char[] line, int position, String value) {
        value.getChars(0, value.length(), line, position - 1);
    }
}
