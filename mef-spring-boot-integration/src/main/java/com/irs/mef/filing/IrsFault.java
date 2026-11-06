package com.irs.mef.filing;

/**
 * A classified failure. {@code logHint} surfaces the one debugging fact that costs hours to
 * rediscover: the real SOAP fault is only in the SDK's rotating a2a_sdk.log, not in our exception.
 */
public record IrsFault(
        String code,
        String message,
        String sdkExceptionClass,
        String detail,
        String logHint) {

    private static final int MAX_DETAIL_LENGTH = 2000;

    static IrsFault wire(String code, Throwable cause, SubmissionId submissionId) {
        return new IrsFault(
                code,
                cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName(),
                cause.getClass().getName(),
                truncate(cause.toString()),
                logHintFor(submissionId));
    }

    static IrsFault noReceipt(String depositId, SubmissionId submissionId) {
        return new IrsFault(
                "NEWSEND_NO_RECEIPT",
                "SendSubmissions returned (deposit id: " + (depositId == null ? "none" : depositId)
                        + ") but no receipt matched submission id " + submissionId.value(),
                null,
                null,
                logHintFor(submissionId));
    }

    static IrsFault lockTimeout() {
        return new IrsFault("NEWSEND_SESSION_BUSY",
                "Could not acquire the IRS session before the configured timeout; nothing was sent",
                null, null, null);
    }

    static IrsFault sessionLost(Exception cause) {
        return new IrsFault("NEWSEND_NOT_LOGGED_IN",
                "The IRS session was gone at transmit time; nothing was sent",
                cause.getClass().getName(), truncate(cause.toString()), null);
    }

    static IrsFault composition(Throwable cause) {
        return new IrsFault("NEWSEND_COMPOSITION",
                "The submission container could not be composed; nothing was sent",
                cause.getClass().getName(), truncate(cause.toString()), null);
    }

    static IrsFault crashRecovery() {
        return new IrsFault("NEWSEND_CRASH_RECOVERY",
                "Found CREATED at journal load — the process died mid-flight; outcome unknown",
                null, null, null);
    }

    private static String logHintFor(SubmissionId submissionId) {
        return "full SOAP fault: grep " + submissionId.value() + " a2a_sdk.log.*";
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= MAX_DETAIL_LENGTH ? text : text.substring(0, MAX_DETAIL_LENGTH);
    }
}
