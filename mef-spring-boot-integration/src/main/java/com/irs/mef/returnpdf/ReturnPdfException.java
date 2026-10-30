package com.irs.mef.returnpdf;

/** The Return cannot be printed faithfully: an unmapped document, an unprinted value, or a bad template. */
public final class ReturnPdfException extends RuntimeException {

    ReturnPdfException(String message) {
        super(message);
    }

    ReturnPdfException(String message, Throwable cause) {
        super(message, cause);
    }
}
