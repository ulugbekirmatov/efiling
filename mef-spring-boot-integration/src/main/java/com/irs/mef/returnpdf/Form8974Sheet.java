package com.irs.mef.returnpdf;

/** IRS8974 onto f8974.pdf. */
final class Form8974Sheet {

    private Form8974Sheet() {}

    static void fill(ReturnXml.Node header, ReturnXml.Node form, Fields fields) {
        throw new ReturnPdfException("IRS8974 is not mapped onto f8974.pdf yet");
    }
}
