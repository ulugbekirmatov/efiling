package com.irs.mef.returnpdf;

/** IRS941X onto f941x.pdf. */
final class Form941XSheet {

    private Form941XSheet() {}

    static void fill(ReturnXml.Node header, ReturnXml.Node form, Fields fields) {
        throw new ReturnPdfException("IRS941X is not mapped onto f941x.pdf yet");
    }
}
