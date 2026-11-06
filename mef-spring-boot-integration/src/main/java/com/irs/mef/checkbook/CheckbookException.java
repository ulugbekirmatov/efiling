package com.irs.mef.checkbook;

import java.util.Map;

/** A non-2xx Checkbook response. {@code moreInfo} maps a field name to its message and may be empty. */
public class CheckbookException extends RuntimeException {

    private final int httpStatus;
    private final String error;
    private final Map<String, String> moreInfo;

    public CheckbookException(int httpStatus, String error, Map<String, String> moreInfo) {
        super("Checkbook HTTP " + httpStatus + ": " + error + (moreInfo.isEmpty() ? "" : " " + moreInfo));
        this.httpStatus = httpStatus;
        this.error = error;
        this.moreInfo = Map.copyOf(moreInfo);
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String error() {
        return error;
    }

    public Map<String, String> moreInfo() {
        return moreInfo;
    }
}
