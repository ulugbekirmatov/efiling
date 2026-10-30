package com.irs.mef.filing;

public record ClientId(String value) {

    public ClientId {
        if (value == null || value.isBlank() || value.length() > 64) {
            throw new FilingValidationException("clientId",
                    "clientId must be non-blank and at most 64 characters");
        }
    }
}
