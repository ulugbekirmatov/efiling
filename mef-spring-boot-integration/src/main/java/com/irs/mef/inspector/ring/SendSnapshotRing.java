package com.irs.mef.inspector.ring;

import java.util.List;
import java.util.Optional;

/**
 * Source of truth for the panel. Not the NewSend journal.
 * commit is idempotent on SnapshotId (replace in place).
 * commit evicts oldest by capturedAt until size &lt;= ringSize.
 * listNewestFirst is a directory scan + sort, not a cached mirror (no index.json to desync).
 */
public interface SendSnapshotRing {

    List<SnapshotSummary> listNewestFirst();

    Optional<SendSnapshot> get(SnapshotId id);

    void commit(SendSnapshot snapshot);

    int size();
}
