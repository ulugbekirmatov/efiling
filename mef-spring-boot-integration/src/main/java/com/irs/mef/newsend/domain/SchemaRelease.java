package com.irs.mef.newsend.domain;

/**
 * An IRS 94x schema release: the Return returnVersion it stamps and the classpath root of its
 * schema tree, copied verbatim so the tree's relative includes resolve unchanged.
 */
public enum SchemaRelease {
    Y2026Q1_V4("2026Q1v4.0", "schemas/94x/2026Q1v4.0/");

    private final String returnVersion;
    private final String bundleRoot;

    SchemaRelease(String returnVersion, String bundleRoot) {
        this.returnVersion = returnVersion;
        this.bundleRoot = bundleRoot;
    }

    public String returnVersion() {
        return returnVersion;
    }

    /** Classpath path of a file inside this release's bundle. */
    public String resolve(String pathInBundle) {
        return bundleRoot + pathInBundle;
    }
}
