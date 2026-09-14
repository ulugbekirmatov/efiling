package com.irs.mef.inspector.ring;

import java.time.Instant;

public record SnapshotSummary(
        SnapshotId submissionId,
        Instant capturedAt,
        String einMasked,
        String formCode,
        String periodLabel,
        boolean soapRequestCaptured) {
}
