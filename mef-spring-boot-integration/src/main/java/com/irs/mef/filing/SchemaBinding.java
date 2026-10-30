package com.irs.mef.filing;

public sealed interface SchemaBinding {

    SchemaRelease release();

    record Bundled(SchemaRelease release, String pathInBundle) implements SchemaBinding {}

    record Pending(SchemaRelease release, String why) implements SchemaBinding {}
}
