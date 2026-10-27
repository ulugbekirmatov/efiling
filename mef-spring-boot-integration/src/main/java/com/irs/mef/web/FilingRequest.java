package com.irs.mef.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Wire request. Strings only. {@link com.irs.mef.filing.ClientFiling#parse} is the boundary.
 * Deliberately absent: a full Return, an EFIN, a submission id, a returnVersion, and an idempotency header.
 */
public record FilingRequest(

        @NotBlank @Size(max = 64)
        String clientId,

        @NotBlank
        String clientBody) {
}
