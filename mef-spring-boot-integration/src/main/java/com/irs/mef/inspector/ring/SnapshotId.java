package com.irs.mef.inspector.ring;

import java.util.Optional;
import java.util.regex.Pattern;

/** 20-char NewSend id. Same grammar; own type so the ring does not import journal records. */
public record SnapshotId(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[0-9]{13}[a-z0-9]{7}$");

    public SnapshotId {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("inspector snapshot id must be a 20-char submission id");
        }
    }

    public static Optional<SnapshotId> tryParse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new SnapshotId(raw.trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
