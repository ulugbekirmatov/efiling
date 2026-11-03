package com.irs.mef.filing;

public record SchemaBinding(SchemaRelease release, String pathInBundle) {

    String resource() {
        return release.resolve(pathInBundle);
    }
}
