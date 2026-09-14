package com.irs.mef.newsend.error;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * The request collides with a prior filing: the idempotency key is already claimed
 * (in flight, transmitted with a different payload, previously rejected) or the
 * (EIN, form, period) was already filed. Never safe to blindly resend.
 */
public final class NewSendDuplicateException extends NewSendException {

    private final String errorCode;
    private final List<String> conflictingSubmissionIds;

    public NewSendDuplicateException(String errorCode, String message, List<String> conflictingSubmissionIds) {
        super(message);
        this.errorCode = errorCode;
        this.conflictingSubmissionIds = List.copyOf(conflictingSubmissionIds);
    }

    public List<String> conflictingSubmissionIds() {
        return conflictingSubmissionIds;
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }

    @Override
    public String errorCode() {
        return errorCode;
    }

    @Override
    public boolean resubmitSafe() {
        return false;
    }
}
