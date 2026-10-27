package com.irs.mef.web;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.irs.mef.filing.ClientFiling;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Wire request. Strings only. {@link com.irs.mef.filing.ClientFiling#parse} is the boundary.
 * Deliberately absent: a full Return, an EFIN, a submission id, a returnVersion, and an idempotency header.
 * {@code allowDuplicatePeriod} defaults to false when the field is omitted.
 */
public record FilingRequest(

        @NotBlank @Size(max = 64)
        String clientId,

        @NotBlank
        @Size(max = ClientFiling.MAX_CLIENT_BODY_CHARS)
        String clientBody,

        boolean allowDuplicatePeriod) {

    @JsonCreator
    public static FilingRequest of(
            @JsonProperty("clientId") String clientId,
            @JsonProperty("clientBody") String clientBody,
            @JsonProperty("allowDuplicatePeriod") Boolean allowDuplicatePeriod) {
        return new FilingRequest(clientId, clientBody, Boolean.TRUE.equals(allowDuplicatePeriod));
    }
}
