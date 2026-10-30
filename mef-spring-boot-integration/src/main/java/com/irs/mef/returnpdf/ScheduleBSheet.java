package com.irs.mef.returnpdf;

/** IRS941ScheduleB onto f941sb.pdf. */
final class ScheduleBSheet {

    private ScheduleBSheet() {}

    static void fill(ReturnXml.Node header, ReturnXml.Node form, Fields fields) {
        throw new ReturnPdfException("IRS941ScheduleB is not mapped onto f941sb.pdf yet");
    }
}
