package com.irs.mef.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for IRS MeF A2A Login operation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {

    /**
     * Login status (success/failure)
     */
    private boolean success;

    /**
     * SAML assertion token received from IRS
     */
    private String samlAssertion;

    /**
     * Session ID or correlation ID
     */
    private String sessionId;

    /**
     * Login timestamp
     */
    private String timestamp;

    /**
     * Message from login response
     */
    private String message;

    /**
     * Error message if login failed
     */
    private String errorMessage;
}
