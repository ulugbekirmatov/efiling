package com.irs.mef.wagefile;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WageFileStoreTest {

    @TempDir
    Path root;

    @Test
    @DisplayName("write then read the same bytes")
    void writeThenReadSameBytes() throws Exception {
        FileWageFileStore store = new FileWageFileStore(root, new ObjectMapper());
        byte[] bytes = "wage-file-bytes-v1".getBytes(StandardCharsets.US_ASCII);

        WageFileRun saved = store.create(2026, List.of("003000004"), bytes, null);
        WageFileRun loaded = store.read(saved.id());

        assertEquals("BUILT", saved.status().name());
        assertEquals(2026, saved.taxYear());
        assertEquals(List.of("003000004"), saved.employerEins());
        assertNull(saved.wageFileIdentifier());
        assertEquals("3168f6854ec346eca537347dc163a1c4bc8476475a5d4562f10980c95104a15b", saved.sha256());
        assertEquals(saved.sha256(), loaded.sha256());
        assertEquals("BUILT", loaded.status().name());
        assertArrayEquals(bytes, Files.readAllBytes(loaded.bytesPath()));
        assertArrayEquals(bytes, store.readBytes(saved.id()));
    }
}
