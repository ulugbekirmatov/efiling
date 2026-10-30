package com.irs.mef.returnpdf;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The one list of printable MeF documents. Each constant is named exactly as its ReturnData
 * element, owns its IRS PDF, and owns the sheet that maps the document onto that PDF's fields.
 */
enum IrsForm {
    IRS941("f941.pdf", 2, Form941Sheet::fill),
    IRS941ScheduleB("f941sb.pdf", 1, ScheduleBSheet::fill),
    IRS941ScheduleR("f941sr.pdf", 2, ScheduleRSheet::fill),
    IRS8974("f8974.pdf", Integer.MAX_VALUE, Form8974Sheet::fill),
    IRS941X("f941x.pdf", Integer.MAX_VALUE, Form941XSheet::fill);

    /** Schema release (Return/@returnVersion) to the IRS PDF revisions bundled for it. */
    private static final Map<String, String> TEMPLATE_DIRECTORY_BY_RETURN_VERSION = Map.of(
            "2026Q1v4.0", "irs-forms/2026/");

    private final String templateFile;
    private final int pagesKept;
    private final Sheet sheet;

    /**
     * @param pagesKept leading pages of the IRS PDF that belong to the return; the 941's third page
     *                  is the 941-V payment voucher, which an e-filed return does not carry
     */
    IrsForm(String templateFile, int pagesKept, Sheet sheet) {
        this.templateFile = templateFile;
        this.pagesKept = pagesKept;
        this.sheet = sheet;
    }

    static IrsForm of(String documentName) {
        return Arrays.stream(values())
                .filter(form -> form.name().equals(documentName))
                .findFirst()
                .orElseThrow(() -> new ReturnPdfException("No paper form for " + documentName
                        + ". Printable documents: " + Arrays.stream(values()).map(Enum::name)
                        .collect(Collectors.joining(", "))));
    }

    FilledForm fill(String returnVersion, ReturnXml.Node header, ReturnXml.Node document) {
        String directory = TEMPLATE_DIRECTORY_BY_RETURN_VERSION.get(returnVersion);
        if (directory == null) {
            throw new ReturnPdfException("No IRS PDF revisions bundled for returnVersion " + returnVersion);
        }
        Fields fields = new Fields();
        sheet.fill(header, document, fields);
        return new FilledForm(this, directory + templateFile, fields.values());
    }

    int pagesKept() {
        return pagesKept;
    }

    @FunctionalInterface
    interface Sheet {
        void fill(ReturnXml.Node header, ReturnXml.Node document, Fields fields);
    }
}
