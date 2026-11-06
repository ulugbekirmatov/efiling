package com.irs.mef.checkbook;

/** Production is intentionally not representable until the sandbox flow is proven. */
public enum CheckbookEnvironment {
    SANDBOX("https://api.sandbox.checkbook.io/v3");

    private final String baseUrl;

    CheckbookEnvironment(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String baseUrl() {
        return baseUrl;
    }
}
