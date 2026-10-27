package com.irs.mef.filing;

/**
 * An IRS 94x schema release. The Software ID env key is named here so a quarterly id cannot be
 * reused for an annual release without a new constant.
 */
public enum SchemaRelease {
    Y2026Q1_V4("2026Q1v4.0", "schemas/94x/2026Q1v4.0/", "MEF_SOFTWARE_ID"),
    Y2026_V2("2026v2.0", "schemas/94x/2026v2.0/", "MEF_SOFTWARE_ID_ANNUAL");

    private final String returnVersion;
    private final String bundleRoot;
    private final String softwareIdEnvKey;

    SchemaRelease(String returnVersion, String bundleRoot, String softwareIdEnvKey) {
        this.returnVersion = returnVersion;
        this.bundleRoot = bundleRoot;
        this.softwareIdEnvKey = softwareIdEnvKey;
    }

    public String returnVersion() {
        return returnVersion;
    }

    /** Env var that holds this release's Software ID. Read only by {@link Originator#fromEnvironment}. */
    String softwareIdEnvKey() {
        return softwareIdEnvKey;
    }

    /** Classpath path of a file inside this release's bundle. */
    String resolve(String pathInBundle) {
        return bundleRoot + pathInBundle;
    }
}
