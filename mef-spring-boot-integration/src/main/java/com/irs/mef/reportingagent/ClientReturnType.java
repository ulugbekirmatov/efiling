package com.irs.mef.reportingagent;

import com.irs.mef.newsend.domain.FormType;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The return types a Reporting Agent client body can carry. Each row owns the header
 * ReturnTypeCd, the NewSend form type (manifest FederalSubmissionTypeCd, schema release and root),
 * the main form that must open ReturnData, and the kind of reporting period its header names.
 */
public enum ClientReturnType {
    F940("940", FormType.F940, "IRS940", PeriodKind.TAX_YEAR),
    F941("941", FormType.F941, "IRS941", PeriodKind.QUARTER),
    F941X("941X", FormType.F941X, "IRS941X", PeriodKind.QUARTER);

    private final String returnTypeCd;
    private final FormType formType;
    private final String mainDocument;
    private final PeriodKind periodKind;

    ClientReturnType(String returnTypeCd, FormType formType, String mainDocument, PeriodKind periodKind) {
        this.returnTypeCd = returnTypeCd;
        this.formType = formType;
        this.mainDocument = mainDocument;
        this.periodKind = periodKind;
    }

    public String returnTypeCd() {
        return returnTypeCd;
    }

    public FormType formType() {
        return formType;
    }

    public String mainDocument() {
        return mainDocument;
    }

    PeriodKind periodKind() {
        return periodKind;
    }

    /** The Return returnVersion; owned by the form's schema release, never by the client body. */
    public String returnVersion() {
        return formType.schemaRelease()
                .orElseThrow(() -> new IllegalStateException("No schema release for form " + formType.code()))
                .returnVersion();
    }

    public static ClientReturnType parse(String raw) {
        return Arrays.stream(values())
                .filter(type -> type.returnTypeCd.equals(raw))
                .findFirst()
                .orElseThrow(() -> new ReportingAgentValidationException("returnTypeCd",
                        "Unknown returnTypeCd '" + raw + "'. Must be one of: " + Arrays.stream(values())
                                .map(ClientReturnType::returnTypeCd)
                                .collect(Collectors.joining(", "))));
    }
}
