package com.irs.mef.ack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileAckStoreTest {

    private static final String SUBMISSION_ID = "2378612026268yagqidh";

    @TempDir
    Path temp;

    @Test
    @DisplayName("a recorded ack reads back field for field")
    void roundTrip() {
        FileAckStore store = new FileAckStore(temp, new ObjectMapper());
        StoredAck ack = stored(SUBMISSION_ID, "Rejected", Instant.parse("2026-09-25T09:26:34Z"),
                AckSource.GET_ACK, List.of("X0000-008"), List.of("bad originator"),
                "003000004", 1234L, true, false);

        store.record(ack);
        Optional<StoredAck> loaded = store.find(SUBMISSION_ID);

        assertEquals(Optional.of(ack), loaded);
    }

    @Test
    @DisplayName("the file carries the IRS operation name and an ISO retrievedAt")
    void fileFormat() throws Exception {
        FileAckStore store = new FileAckStore(temp, new ObjectMapper());

        store.record(ack("Accepted", "2026-09-25T09:26:34Z", AckSource.GET_NEW_ACKS));

        JsonNode json = new ObjectMapper().readTree(temp.resolve(SUBMISSION_ID + ".json").toFile());
        assertEquals("GetNewAcks", json.get("source").asText());
        assertEquals("2026-09-25T09:26:34Z", json.get("retrievedAt").asText());
        assertEquals("Accepted", json.get("ackType").asText());
    }

    @Test
    @DisplayName("the latest retrieval replaces the earlier one and leaves no temp file")
    void latestRetrievalReplaces() throws Exception {
        FileAckStore store = new FileAckStore(temp, new ObjectMapper());
        store.record(ack("Rejected", "2026-09-25T09:00:00Z", AckSource.GET_NEW_ACKS));

        store.record(ack("Accepted", "2026-09-25T10:00:00Z", AckSource.GET_ACK));

        StoredAck loaded = store.find(SUBMISSION_ID).orElseThrow();
        assertEquals("Accepted", loaded.ackType());
        assertEquals(AckSource.GET_ACK, loaded.source());
        assertEquals(Instant.parse("2026-09-25T10:00:00Z"), loaded.retrievedAt());
        try (var files = Files.list(temp)) {
            assertEquals(List.of(SUBMISSION_ID + ".json"),
                    files.map(path -> path.getFileName().toString()).toList());
        }
    }

    @Test
    @DisplayName("unknown or malformed ids find nothing and cannot escape the store directory")
    void unknownAndMalformedIds() {
        FileAckStore store = new FileAckStore(temp, new ObjectMapper());

        assertTrue(store.find(SUBMISSION_ID).isEmpty());
        assertTrue(store.find("../../etc/passwd").isEmpty());
        assertThrows(RuntimeException.class, () -> store.record(stored(
                "../escape", "Accepted", Instant.EPOCH, AckSource.GET_ACK,
                null, null, null, null, null, null)));
        assertFalse(Files.exists(temp.resolve("../escape.json")));
    }

    private static StoredAck ack(String ackType, String retrievedAt, AckSource source) {
        return stored(SUBMISSION_ID, ackType, Instant.parse(retrievedAt), source,
                null, null, null, null, null, null);
    }

    private static StoredAck stored(String submissionId, String ackType, Instant retrievedAt, AckSource source,
                                    List<String> errorCodes, List<String> errorMessages, String ein,
                                    Long totalTaxAmt, Boolean hasValidationErrors, Boolean hasValidationAlerts) {
        return new StoredAck(
                null, submissionId, retrievedAt, source, ackType, null, null,
                errorCodes, errorMessages, null, null, ein, null, null, totalTaxAmt,
                null, null, null, null, null, null, null, null, null, null, null, null,
                hasValidationErrors, hasValidationAlerts);
    }
}
