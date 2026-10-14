package com.irs.mef.reportingagent;

import com.irs.mef.newsend.domain.FormType;

/**
 * Refuses a live ATS run before login when the form's bundled root XSD is not on the classpath.
 * Schema validation runs inside submit, after the IRS session is already open.
 */
final class SchemaPreflight {

    private SchemaPreflight() {}

    static void require(FormType formType) {
        String root = formType.schemaRoot().orElseThrow(() -> new IllegalStateException(
                "No schema bundled for " + formType.code() + "; no IRS session was opened"));
        if (SchemaPreflight.class.getClassLoader().getResource(root) == null) {
            throw new IllegalStateException(
                    "Bundled schema missing from classpath: " + root + "; no IRS session was opened");
        }
    }
}
