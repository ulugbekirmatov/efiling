package com.irs.mef.filing;

/**
 * Where a form's root XSD is. {@link Pending} has no path, so a placeholder path cannot be written.
 */
public sealed interface SchemaBinding {

    SchemaRelease release();

    record Bundled(SchemaRelease release, String pathInBundle) implements SchemaBinding {}

    record Pending(SchemaRelease release, String why) implements SchemaBinding {}
}
