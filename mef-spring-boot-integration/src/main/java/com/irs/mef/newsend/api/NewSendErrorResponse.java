package com.irs.mef.newsend.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

/**
 * Error body for the NewSend endpoints. {@code resubmitSafe} is first-class because it is the
 * only question a filing caller actually has: may this request be sent again without risking
 * a duplicate federal filing?
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record NewSendErrorResponse(
        Instant timestamp,
        int status,
        String errorCode,
        String message,
        String field,
        String submissionId,
        List<String> conflictingSubmissionIds,
        Boolean resubmitSafe,
        String reconcileWith,
        String logHint) {
}
