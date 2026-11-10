package com.irs.mef.returnpdf;

public final class ReturnPdfException extends RuntimeException {

    ReturnPdfException(String message) {
        super(message);
    }

    ReturnPdfException(String message, Throwable cause) {
        super(message, cause);
    }
}
