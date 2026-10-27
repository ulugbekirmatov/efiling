package com.irs.mef.filing;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The fileable 94x forms. The return type code and the manifest code are the same constant,
 * and each form is either bundled or pending.
 */
public enum FormSpec {
    F940("940", "IRS940", PeriodKind.TAX_YEAR,
            new SchemaBinding.Pending(SchemaRelease.Y2026_V2,
                    "2026v2.0 bundle arrives via the e-Services SOR mailbox; element names in 940 fixtures are unverified")),
    F941("941", "IRS941", PeriodKind.QUARTER,
            new SchemaBinding.Bundled(SchemaRelease.Y2026Q1_V4, "EmploymentTax/941/Return941.xsd")),
    F941X("941X", "IRS941X", PeriodKind.QUARTER,
            new SchemaBinding.Bundled(SchemaRelease.Y2026Q1_V4, "EmploymentTax/941X/Return941X.xsd"));

    private final String code;
    private final String mainDocument;
    private final PeriodKind periodKind;
    private final SchemaBinding schema;

    FormSpec(String code, String mainDocument, PeriodKind periodKind, SchemaBinding schema) {
        this.code = code;
        this.mainDocument = mainDocument;
        this.periodKind = periodKind;
        this.schema = schema;
    }

    public String code() {
        return code;
    }

    String mainDocument() {
        return mainDocument;
    }

    PeriodKind periodKind() {
        return periodKind;
    }

    public SchemaBinding schema() {
        return schema;
    }

    public String returnVersion() {
        return schema.release().returnVersion();
    }

    public static FormSpec parse(String raw) {
        return Arrays.stream(values())
                .filter(form -> form.code.equals(raw))
                .findFirst()
                .orElseThrow(() -> new ReportingAgentValidationException("returnTypeCd",
                        "Unknown returnTypeCd '" + raw + "'. Must be one of: " + legalCodes()));
    }

    private static String legalCodes() {
        return Arrays.stream(values()).map(FormSpec::code).collect(Collectors.joining(", "));
    }
}
