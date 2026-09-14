package com.irs.mef.inspector.ring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory ring for tests. Same last-N / replace-in-place rules as the file ring. */
public final class InMemorySendSnapshotRing implements SendSnapshotRing {

    private final int ringSize;
    private final Map<String, SendSnapshot> byId = new LinkedHashMap<>();

    public InMemorySendSnapshotRing(int ringSize) {
        if (ringSize < 1 || ringSize > 100) {
            throw new IllegalArgumentException("ringSize 1..100");
        }
        this.ringSize = ringSize;
    }

    @Override
    public synchronized List<SnapshotSummary> listNewestFirst() {
        List<SendSnapshot> snapshots = new ArrayList<>(byId.values());
        snapshots.sort(Comparator.comparing(SendSnapshot::capturedAt).reversed());
        List<SnapshotSummary> summaries = new ArrayList<>(snapshots.size());
        for (SendSnapshot snapshot : snapshots) {
            summaries.add(snapshot.summary());
        }
        return List.copyOf(summaries);
    }

    @Override
    public synchronized Optional<SendSnapshot> get(SnapshotId id) {
        return Optional.ofNullable(byId.get(id.value()));
    }

    @Override
    public synchronized void commit(SendSnapshot snapshot) {
        byId.remove(snapshot.submissionId().value());
        byId.put(snapshot.submissionId().value(), snapshot);
        while (byId.size() > ringSize) {
            String oldest = byId.values().stream()
                    .min(Comparator.comparing(SendSnapshot::capturedAt))
                    .map(s -> s.submissionId().value())
                    .orElseThrow();
            byId.remove(oldest);
        }
    }

    @Override
    public synchronized int size() {
        return byId.size();
    }

    public synchronized void clear() {
        byId.clear();
    }
}
