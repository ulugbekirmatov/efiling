package com.irs.mef.returnpdf;

import java.util.List;

/**
 * The paper copy of a composed MeF Return: the official IRS fillable PDF of every document in
 * ReturnData, filled from the Return XML and flattened, in ReturnData order.
 *
 * Rendering fails rather than drop a value. Every value inside ReturnData, plus the filer, the
 * period, and the third-party designee answer in the header, must land on a field or be declared
 * as not printed with a reason ({@link ReturnXml.Node#notPrinted}).
 */
public final class ReturnPdf {

    private ReturnPdf() {}

    public static byte[] render(String returnXml) {
        return PdfAssembly.assemble(fill(returnXml));
    }

    static List<FilledForm> fill(String returnXml) {
        ReturnXml parsed = ReturnXml.parse(returnXml);
        List<FilledForm> forms = parsed.documents().stream()
                .map(document -> IrsForm.of(document.name())
                        .fill(parsed.returnVersion(), parsed.header(), document))
                .toList();
        parsed.requireAllPrinted();
        return forms;
    }
}
