package com.irs.mef.newsend.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.irs.mef.newsend.error.NewSendValidationException;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The 94x family of the FederalSubmissionTypeCd enumeration, verbatim from efileAttachments.xsd.
 * Closed on purpose: an unknown form type is a 400 at the boundary, never an IRS reject
 * two minutes later. The legacy service hardcoded "941"; here the form is always explicit.
 */
public enum FormType {
    F940("940"),
    F940PR("940PR"),
    F941("941", SchemaRelease.Y2026Q1_V4, "EmploymentTax/941/Return941.xsd"),
    F941PR("941PR"),
    F941SS("941SS"),
    F941X("941X", SchemaRelease.Y2026Q1_V4, "EmploymentTax/941X/Return941X.xsd"),
    F943("943"),
    F943X("943X"),
    F943PR("943PR"),
    F944("944"),
    F945("945"),
    F945X("945X"),
    F94XPINREG("94XPINREG");

    private final String code;
    private final SchemaRelease schemaRelease;
    private final String pathInBundle;

    FormType(String code) {
        this(code, null, null);
    }

    FormType(String code, SchemaRelease schemaRelease, String pathInBundle) {
        this.code = code;
        this.schemaRelease = schemaRelease;
        this.pathInBundle = pathInBundle;
    }

    /** The wire value used in the manifest and in JSON. */
    @JsonValue
    public String code() {
        return code;
    }

    /** Boundary parse. Accepts the wire code (e.g. "941"), never the enum constant name. */
    public static FormType parse(String raw) {
        return Arrays.stream(values())
                .filter(formType -> formType.code.equals(raw))
                .findFirst()
                .orElseThrow(() -> new NewSendValidationException("formType",
                        "Unknown form type '" + raw + "'. Must be one of: " + legalCodes()));
    }

    /** The schema release this form is filed under; empty for a form with no bundled schema. */
    public Optional<SchemaRelease> schemaRelease() {
        return Optional.ofNullable(schemaRelease);
    }

    /** Classpath root XSD for this form when one is bundled; empty means schema validation degrades to a warning. */
    public Optional<String> schemaRoot() {
        return schemaRelease().map(release -> release.resolve(pathInBundle));
    }

    private static String legalCodes() {
        return Arrays.stream(values()).map(FormType::code).collect(Collectors.joining(", "));
    }
}
