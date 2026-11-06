package com.irs.mef.filing;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public enum FormSpec {
    F940("940", "IRS940", PeriodKind.TAX_YEAR, "EmploymentTax/940/Return940.xsd",
            Map.of(2026, SchemaRelease.Y2026_V2)),
    F941("941", "IRS941", PeriodKind.QUARTER, "EmploymentTax/941/Return941.xsd",
            Map.of(2026, SchemaRelease.Y2026Q1_V4, 2027, SchemaRelease.Y2027Q1_V2)),
    F941X("941X", "IRS941X", PeriodKind.QUARTER, "EmploymentTax/941X/Return941X.xsd",
            Map.of(2026, SchemaRelease.Y2026Q1_V4, 2027, SchemaRelease.Y2027Q1_V2));

    private final String code;
    private final String mainDocument;
    private final PeriodKind periodKind;
    private final String xsdPathInBundle;
    private final Map<Integer, SchemaRelease> releaseByTaxYear;

    FormSpec(String code, String mainDocument, PeriodKind periodKind, String xsdPathInBundle,
             Map<Integer, SchemaRelease> releaseByTaxYear) {
        this.code = code;
        this.mainDocument = mainDocument;
        this.periodKind = periodKind;
        this.xsdPathInBundle = xsdPathInBundle;
        this.releaseByTaxYear = Map.copyOf(releaseByTaxYear);
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

    /** The IRS schema release a return of this form for the given tax year is composed and validated against. */
    public SchemaBinding schemaFor(int taxYear) {
        SchemaRelease release = releaseByTaxYear.get(taxYear);
        if (release == null) {
            throw new ReportingAgentValidationException("taxPeriodBegin",
                    "No bundled " + code + " schema for tax year " + taxYear + ". Bundled tax years: "
                            + new TreeMap<>(releaseByTaxYear).keySet().stream()
                            .map(String::valueOf).collect(Collectors.joining(", ")));
        }
        return new SchemaBinding(release, xsdPathInBundle);
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
