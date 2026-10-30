package com.irs.mef.returnpdf;

/** IRS941ScheduleR onto f941sr.pdf. */
final class ScheduleRSheet {

    private ScheduleRSheet() {}

    static void fill(ReturnXml.Node header, ReturnXml.Node form, Fields fields) {
        throw new ReturnPdfException("IRS941ScheduleR is not mapped onto f941sr.pdf yet");
    }
}
