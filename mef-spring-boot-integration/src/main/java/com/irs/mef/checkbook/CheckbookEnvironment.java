package com.irs.mef.checkbook;

/** Which Checkbook.io API host the client talks to. PRODUCTION moves real money; see {@link CheckbookProperties}. */
public enum CheckbookEnvironment {
    SANDBOX("https://api.sandbox.checkbook.io/v3"),
    PRODUCTION("https://api.checkbook.io/v3");

    private final String baseUrl;

    CheckbookEnvironment(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String baseUrl() {
        return baseUrl;
    }
}
