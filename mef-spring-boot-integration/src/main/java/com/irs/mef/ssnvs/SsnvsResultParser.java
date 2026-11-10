package com.irs.mef.ssnvs;

import com.irs.mef.ssnvs.SsnvsInputException.Problem;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The results-file boundary (SSNVS Handbook pp.20-21). Strict: every line is 130 characters and echoes
 * {@code 214}, {@code OEVS} and {@code 000}, or the whole file is refused. Errors name the line, never content.
 */
final class SsnvsResultParser {

    static final String RESULTS_FILE = "results";

    /** Result layout, 1-based inclusive positions. */
    enum Field {
        SSN_LAST_FOUR(6, 9),
        ECHO(10, 47),
        CONFIRMATION(49, 83),
        CONTROL(84, 97),
        VERIFICATION_CODE(98, 98),
        PROCESSING_CODE(99, 101),
        REQUESTER_ID(102, 105),
        MULTIPLE_REQUEST(106, 108),
        DEATH(121, 121);

        final int start;
        final int end;

        Field(int start, int end) {
            this.start = start;
            this.end = end;
        }

        String in(String line) {
            return line.substring(start - 1, end);
        }
    }

    private SsnvsResultParser() {
    }

    static List<SsnvsResult> parse(byte[] results) {
        List<String> lines = new ArrayList<>();
        for (String line : new String(results, StandardCharsets.ISO_8859_1).split("\n", -1)) {
            lines.add(stripOneCarriageReturn(line));
        }
        dropIfEmpty(lines);
        dropIfEmpty(lines);
        List<SsnvsResult> parsed = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            parsed.add(parseLine(lines.get(i), i + 1));
        }
        return parsed;
    }

    /** Called twice: once for the text after the final line feed, once for one blank trailing line. */
    private static void dropIfEmpty(List<String> lines) {
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
    }

    private static String stripOneCarriageReturn(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    private static SsnvsResult parseLine(String line, int lineNumber) {
        if (line.length() != SsnvsRequest.RECORD_LENGTH
                || !SsnvsRequest.PROCESSING_CODE.equals(Field.PROCESSING_CODE.in(line))
                || !SsnvsRequest.REQUESTER_ID.equals(Field.REQUESTER_ID.in(line))
                || !SsnvsRequest.MULTIPLE_REQUEST.equals(Field.MULTIPLE_REQUEST.in(line))) {
            throw new SsnvsInputException(Problem.BAD_RESULT_RECORD, lineNumber, RESULTS_FILE);
        }
        return new SsnvsResult(
                lineNumber,
                Field.CONTROL.in(line),
                Field.SSN_LAST_FOUR.in(line),
                new SsnvsResult.Echo(Field.ECHO.in(line)),
                Verification.of(Field.VERIFICATION_CODE.in(line).charAt(0)),
                Death.of(Field.DEATH.in(line).charAt(0)),
                Field.CONFIRMATION.in(line).trim());
    }
}
