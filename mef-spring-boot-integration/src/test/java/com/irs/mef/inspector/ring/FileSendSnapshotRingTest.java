package com.irs.mef.inspector.ring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irs.mef.inspector.FixtureSnapshots;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSendSnapshotRingTest {

    @TempDir
    Path temp;

    @Test
    @DisplayName("commit is idempotent on SnapshotId; extra commit does not grow the ring")
    void secondCommitSameIdDoesNotGrowRing() {
        FileSendSnapshotRing ring = new FileSendSnapshotRing(temp, 20, new ObjectMapper());
        SendSnapshot snap = FixtureSnapshots.orchidQ1();
        ring.commit(snap);
        ring.commit(snap);
        assertEquals(1, ring.size());
        Optional<SendSnapshot> loaded = ring.get(snap.submissionId());
        assertTrue(loaded.isPresent());
        assertEquals(snap.returnXml().original(), loaded.get().returnXml().original());
        assertEquals(snap.returnXmlSha256(), loaded.get().returnXml().sha256());
        assertTrue(Files.exists(temp.resolve(snap.submissionId().value()).resolve("return.xml")));
        assertFalse(Files.exists(temp.resolve(snap.submissionId().value()).resolve("container.zip")));
    }

    @Test
    @DisplayName("commit evicts oldest by capturedAt once past ring size")
    void evictsOldestPastN() {
        FileSendSnapshotRing ring = new FileSendSnapshotRing(temp, 2, new ObjectMapper());
        SendSnapshot a = withIdAndTime(FixtureSnapshots.orchidQ1(), "2386892026090aaaa111",
                Instant.parse("2026-03-31T12:00:00Z"));
        SendSnapshot b = withIdAndTime(FixtureSnapshots.orchidQ1(), "2386892026090bbbb222",
                Instant.parse("2026-03-31T12:01:00Z"));
        SendSnapshot c = withIdAndTime(FixtureSnapshots.orchidQ1(), "2386892026090cccc333",
                Instant.parse("2026-03-31T12:02:00Z"));
        ring.commit(a);
        ring.commit(b);
        ring.commit(c);
        assertEquals(2, ring.size());
        assertTrue(ring.get(b.submissionId()).isPresent());
        assertTrue(ring.get(c.submissionId()).isPresent());
        assertTrue(ring.get(a.submissionId()).isEmpty());
        assertEquals(c.submissionId().value(), ring.listNewestFirst().get(0).submissionId().value());
    }

    @Test
    @DisplayName("a new ring on the same path reloads from directories, not an index.json")
    void restartReloadsFromDirectories() {
        FileSendSnapshotRing first = new FileSendSnapshotRing(temp, 20, new ObjectMapper());
        first.commit(FixtureSnapshots.orchidQ1());
        FileSendSnapshotRing second = new FileSendSnapshotRing(temp, 20, new ObjectMapper());
        assertEquals(1, second.size());
        assertEquals(FixtureSnapshots.SUBMISSION_ID, second.listNewestFirst().get(0).submissionId().value());
        assertFalse(Files.exists(temp.resolve("index.json")));
    }

    private static SendSnapshot withIdAndTime(SendSnapshot template, String id, Instant capturedAt) {
        return new SendSnapshot(
                new SnapshotId(id),
                capturedAt,
                template.environment(),
                template.einMasked(),
                template.formType(),
                template.taxPeriod(),
                template.clientRequestId(),
                template.returnXmlSha256(),
                template.returnXml(),
                template.manifestXml(),
                template.mimeRequest(),
                template.soapResponse());
    }
}
