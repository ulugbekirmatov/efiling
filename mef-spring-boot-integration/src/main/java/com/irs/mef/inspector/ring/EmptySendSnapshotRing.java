package com.irs.mef.inspector.ring;

import java.util.List;
import java.util.Optional;

/** Bound for {@code mef.inspector.enabled=false}: the panel still serves empty-state HTML. */
public final class EmptySendSnapshotRing implements SendSnapshotRing {

    @Override
    public List<SnapshotSummary> listNewestFirst() {
        return List.of();
    }

    @Override
    public Optional<SendSnapshot> get(SnapshotId id) {
        return Optional.empty();
    }

    @Override
    public void commit(SendSnapshot snapshot) {
        // capture disabled
    }

    @Override
    public int size() {
        return 0;
    }
}
