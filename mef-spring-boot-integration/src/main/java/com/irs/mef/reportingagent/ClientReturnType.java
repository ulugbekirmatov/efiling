package com.irs.mef.reportingagent;

import com.irs.mef.newsend.domain.FormType;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The return types a Reporting Agent client body can carry. Each row owns the header
 * ReturnTypeCd, the NewSend form type (manifest FederalSubmissionTypeCd and schema root),
 * and the main form that must open ReturnData.
 */
public enum ClientReturnType {
    F941("941", FormType.F941, "IRS941"),
    F941X("941X", FormType.F941X, "IRS941X");

    private final String returnTypeCd;
    private final FormType formType;
    private final String mainDocument;

    ClientReturnType(String returnTypeCd, FormType formType, String mainDocument) {
        this.returnTypeCd = returnTypeCd;
        this.formType = formType;
        this.mainDocument = mainDocument;
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
