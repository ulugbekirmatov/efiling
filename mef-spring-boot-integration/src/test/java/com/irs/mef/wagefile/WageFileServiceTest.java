package com.irs.mef.wagefile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irs.mef.wagefile.api.WageFileController;
import com.irs.mef.wagefile.api.WageFileErrorResponse;
import com.irs.mef.wagefile.api.WageFileReceiptRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WageFileServiceTest {

    @TempDir
    Path root;

    private FileWageFileStore store;
    private WageFileService service;
    private WageFileController controller;

    @BeforeEach
    void setUp() {
        store = new FileWageFileStore(root, new ObjectMapper());
        service = new WageFileService(
                new FixtureWageYearSource(), new Efw2Writer(), new Efw2Validator(), store);
        controller = new WageFileController(service);
    }

    @Test
    @DisplayName("build stores the fixture year as BUILT")
    void build() throws Exception {
        WageFileRun run = service.build(2026);
        byte[] bytes = Files.readAllBytes(run.bytesPath());
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        String[] records = new String(bytes, StandardCharsets.US_ASCII).split("\r\n");

        assertEquals(36, run.id().length());
        assertEquals(root.resolve(run.id()).resolve("efw2"), run.bytesPath());
        assertEquals(sha256, run.sha256());
        assertEquals("BUILT", run.status().name());
        assertEquals(5, records.length);
        assertEquals("003000004", FixedWidth.parse(records[1]).get(8, 16));
        assertEquals("ORCHID", FixedWidth.parse(records[1]).get(40, 96).stripTrailing());
        assertEquals("000000000", FixedWidth.parse(records[2]).get(3, 11));
        assertEquals("TEST", FixedWidth.parse(records[2]).get(12, 26).stripTrailing());
        assertEquals("PERSON", FixedWidth.parse(records[2]).get(42, 61).stripTrailing());
        assertEquals("00000250000", FixedWidth.parse(records[2]).get(188, 198));
        assertEquals("00000025000", FixedWidth.parse(records[2]).get(199, 209));
        assertEquals("00000250000", FixedWidth.parse(records[2]).get(210, 220));
        assertEquals("00000031000", FixedWidth.parse(records[2]).get(221, 231));
        assertEquals("00000250000", FixedWidth.parse(records[2]).get(232, 242));
        assertEquals("00000007250", FixedWidth.parse(records[2]).get(243, 253));
    }

    @Test
    @DisplayName("receipt stores a 6-character identifier and sets UPLOADED")
    void receipt() {
        WageFileRun built = service.build(2026);
        byte[] before = store.readBytes(built.id());

        WageFileRun uploaded = service.recordReceipt(built.id(), "ABC123");

        assertEquals("UPLOADED", uploaded.status().name());
        assertEquals("ABC123", uploaded.wageFileIdentifier());
        assertEquals("UPLOADED", store.read(built.id()).status().name());
        assertEquals("ABC123", store.read(built.id()).wageFileIdentifier());
        assertArrayEquals(before, store.readBytes(built.id()));
    }

    @Test
    @DisplayName("an identifier that is not 6 characters is rejected with 400")
    void receiptRejectsOtherLengths() {
        WageFileRun built = service.build(2026);

        WageFileValidationException tooShort = assertThrows(
                WageFileValidationException.class, () -> service.recordReceipt(built.id(), "ABC12"));
        assertEquals("wageFileIdentifier must be 6 characters", tooShort.getMessage());
        WageFileValidationException tooLong = assertThrows(
                WageFileValidationException.class, () -> service.recordReceipt(built.id(), "ABC1234"));
        assertEquals("wageFileIdentifier must be 6 characters", tooLong.getMessage());

        ResponseEntity<?> shortResponse = controller.receipt(built.id(), new WageFileReceiptRequest("ABC12"));
        ResponseEntity<?> longResponse = controller.receipt(built.id(), new WageFileReceiptRequest("ABC1234"));
        assertEquals(400, shortResponse.getStatusCode().value());
        assertEquals(400, longResponse.getStatusCode().value());
        assertEquals("wageFileIdentifier must be 6 characters",
                ((WageFileErrorResponse) shortResponse.getBody()).message());
        assertEquals("BUILT", store.read(built.id()).status().name());
    }

    @Test
    @DisplayName("resubmit sets RA 29-35 on a new run and leaves the original bytes")
    void resubmit() {
        WageFileRun uploaded = service.recordReceipt(service.build(2026).id(), "ABC123");
        byte[] before = store.readBytes(uploaded.id());

        WageFileRun next = service.resubmit(uploaded.id());

        assertArrayEquals(before, store.readBytes(uploaded.id()));
        String original = new String(store.readBytes(uploaded.id()), StandardCharsets.US_ASCII);
        String resent = new String(store.readBytes(next.id()), StandardCharsets.US_ASCII);
        assertEquals("0", FixedWidth.parse(original.split("\r\n")[0]).get(29, 29));
        assertEquals("1", FixedWidth.parse(resent.split("\r\n")[0]).get(29, 29));
        assertEquals("ABC123", FixedWidth.parse(resent.split("\r\n")[0]).get(30, 35));
        assertEquals(original.substring(0, 28), resent.substring(0, 28));
        assertEquals(original.substring(35), resent.substring(35));
        assertEquals(uploaded.id(), next.priorRunId());
        assertEquals("BUILT", next.status().name());
    }
}
