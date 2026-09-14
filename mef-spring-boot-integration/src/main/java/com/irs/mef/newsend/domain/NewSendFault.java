package com.irs.mef.newsend.domain;

/**
 * A classified failure. {@code logHint} surfaces the one debugging fact that costs hours to
 * rediscover: the real SOAP fault is only in the SDK's rotating a2a_sdk.log, not in our exception.
 */
public record NewSendFault(
        String code,
        String message,
        String sdkExceptionClass,
        String detail,
        String logHint) {

    private static final int MAX_DETAIL_LENGTH = 2000;

    public static NewSendFault wire(String code, Throwable cause, NewSendSubmissionId submissionId) {
        return new NewSendFault(
                code,
                cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName(),
                cause.getClass().getName(),
                truncate(cause.toString()),
                logHintFor(submissionId));
    }

    public static NewSendFault noReceipt(String depositId, NewSendSubmissionId submissionId) {
        return new NewSendFault(
                "NEWSEND_NO_RECEIPT",
                "SendSubmissions returned (deposit id: " + (depositId == null ? "none" : depositId)
                        + ") but no receipt matched submission id " + submissionId.value(),
                null,
                null,
                logHintFor(submissionId));
    }

    public static NewSendFault lockTimeout() {
        return new NewSendFault("NEWSEND_SESSION_BUSY",
                "Could not acquire the IRS session before the configured timeout; nothing was sent",
                null, null, null);
    }

    public static NewSendFault sessionLost(Exception cause) {
        return new NewSendFault("NEWSEND_NOT_LOGGED_IN",
                "The IRS session was gone at transmit time; nothing was sent",
                cause.getClass().getName(), truncate(cause.toString()), null);
    }

    public static NewSendFault composition(Throwable cause) {
        return new NewSendFault("NEWSEND_COMPOSITION",
                "The submission container could not be composed; nothing was sent",
                cause.getClass().getName(), truncate(cause.toString()), null);
    }

    public static NewSendFault crashRecovery() {
        return new NewSendFault("NEWSEND_CRASH_RECOVERY",
                "Found CREATED at journal load — the process died mid-flight; outcome unknown",
                null, null, null);
    }

    private static String logHintFor(NewSendSubmissionId submissionId) {
        return "full SOAP fault: grep " + submissionId.value() + " a2a_sdk.log.*";
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= MAX_DETAIL_LENGTH ? text : text.substring(0, MAX_DETAIL_LENGTH);
    }
}
