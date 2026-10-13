package com.irs.mef.ssnvs;

import com.irs.mef.ssnvs.SsnvsInputException.Problem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SsnvsResultParserTest {

    private static final String REQUEST = new SsnvsRequest(new Ssn(SsnvsFixtures.ssn(3)),
            new LastName(SsnvsFixtures.last(3)), new FirstName(SsnvsFixtures.first(3)), null, null,
            new ControlId(SsnvsFixtures.employeeId(3))).record();

    private static List<SsnvsResult> parse(String text) {
        return SsnvsResultParser.parse(text.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    @DisplayName("a result line yields control, code, death, confirmation and the unmasked last four")
    void fields() {
        SsnvsResult result = parse(FakeSsaResults.line(REQUEST, '5', 'Y', FakeSsaResults.CONFIRMATION)).get(0);

        assertEquals("00000000004103", result.control());
        assertEquals(Verification.NAME_MISMATCH, result.verification());
        assertEquals(Death.YES, result.death());
        assertEquals(FakeSsaResults.CONFIRMATION, result.confirmation());
        assertEquals(SsnvsFixtures.ssn(3).substring(5), result.ssnLastFour());
        assertEquals(1, result.line());
        assertFalse(result.toString().contains(SsnvsFixtures.last(3)));
    }

    @Test
    @DisplayName("every documented code maps; 7, and the retired gender codes 2 and 4, are UNKNOWN")
    void codes() {
        String codes = " 135672";
        List<Verification> expected = List.of(Verification.VERIFIED, Verification.SSN_NOT_IN_FILE,
                Verification.BIRTH_DATE_MISMATCH, Verification.NAME_MISMATCH, Verification.NOT_VERIFIED_OTHER,
                Verification.UNKNOWN, Verification.UNKNOWN);
        for (int i = 0; i < codes.length(); i++) {
            String line = FakeSsaResults.line(REQUEST, codes.charAt(i), 'N', FakeSsaResults.CONFIRMATION);
            assertEquals(expected.get(i), parse(line).get(0).verification(), "code '" + codes.charAt(i) + "'");
        }
        assertEquals(Verification.UNKNOWN, Verification.of('4'));
        assertEquals(Death.UNKNOWN, parse(FakeSsaResults.line(REQUEST, ' ', ' ', "")).get(0).death());
    }

    @Test
    @DisplayName("LF or CRLF line ends and one blank trailing line are accepted")
    void lineEnds() {
        String line = FakeSsaResults.line(REQUEST, ' ', 'N', FakeSsaResults.CONFIRMATION);

        assertEquals(2, parse(line + "\n" + line + "\r\n\r\n").size());
        assertEquals(1, parse(line).size());
    }

    @Test
    @DisplayName("a short line or a wrong echoed literal refuses the file, naming the line")
    void strict() {
        String line = FakeSsaResults.line(REQUEST, ' ', 'N', FakeSsaResults.CONFIRMATION);

        SsnvsInputException shortLine = assertThrows(SsnvsInputException.class,
                () -> parse(line + "\r\n" + line.substring(1) + "\r\n"));
        assertEquals(Problem.BAD_RESULT_RECORD, shortLine.problem());
        assertEquals(2, shortLine.line());
        assertThrows(SsnvsInputException.class, () -> parse(line.replace("214OEVS000", "214OEVX000")));
        assertThrows(SsnvsInputException.class, () -> parse(line + "\r\n\r\n\r\n"));
        assertThrows(SsnvsInputException.class, () -> parse(REQUEST));
    }

    @Test
    @DisplayName("fake SSA results use handbook positions and do not call production field constants")
    void handbookPositions() throws IOException {
        String source = Files.readString(Path.of("src/test/java/com/irs/mef/ssnvs/FakeSsaResults.java"));
        assertEquals(-1, source.indexOf("SsnvsRequest"));
        assertEquals(-1, source.indexOf("Field"));

        String line = FakeSsaResults.line(REQUEST, ' ', 'N', "ZQCONFIRM0000001");
        assertEquals(130, line.length());
        assertEquals("XXXXX" + REQUEST.substring(5, 9), line.substring(0, 9));
        assertEquals(REQUEST.substring(15, 28), line.substring(9, 22));
        assertEquals(REQUEST.substring(28, 38), line.substring(22, 32));
        assertEquals(REQUEST.substring(38, 45), line.substring(32, 39));
        assertEquals(REQUEST.substring(45, 53), line.substring(39, 47));
        assertEquals(' ', line.charAt(47));
        assertEquals("ZQCONFIRM0000001", line.substring(48, 83).stripTrailing());
        assertEquals(REQUEST.substring(89, 103), line.substring(83, 97));
        assertEquals(' ', line.charAt(97));
        assertEquals("214", line.substring(98, 101));
        assertEquals("OEVS", line.substring(101, 105));
        assertEquals("000", line.substring(105, 108));
        assertEquals('N', line.charAt(120));

        String unmasked = FakeSsaResults.line(REQUEST, '1', 'Y', "ZQCONFIRM0000001");
        assertEquals(REQUEST.substring(0, 9), unmasked.substring(0, 9));
        assertEquals('1', unmasked.charAt(97));
        assertEquals('Y', unmasked.charAt(120));
    }
}
