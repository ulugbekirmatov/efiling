package com.irs.mef.reportingagent;

import com.irs.mef.newsend.domain.SchemaRelease;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * IRS issues a Software ID per software package, and the quarterly and annual 94x forms are
 * separate packages, so a 940 must not reuse the ID that passed the 941 scenarios.
 */
public record SoftwareIds(Map<SchemaRelease, SoftwareId> byRelease) {

    public SoftwareIds {
        byRelease = Map.copyOf(Objects.requireNonNull(byRelease));
    }

    public static SoftwareIds of(SchemaRelease release, SoftwareId softwareId) {
        return new SoftwareIds(Map.of(release, softwareId));
    }

    public SoftwareIds with(SchemaRelease release, SoftwareId softwareId) {
        Map<SchemaRelease, SoftwareId> next = new EnumMap<>(SchemaRelease.class);
        next.putAll(byRelease);
        next.put(release, softwareId);
        return new SoftwareIds(next);
    }

    public SoftwareId forRelease(SchemaRelease release) {
        SoftwareId softwareId = byRelease.get(release);
        if (softwareId == null) {
            throw new ReportingAgentValidationException("softwareId",
                    "No Software ID configured for returnVersion " + release.returnVersion());
        }
        return softwareId;
    }
}
