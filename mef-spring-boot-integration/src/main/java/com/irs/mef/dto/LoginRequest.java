package com.irs.mef.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for IRS MeF A2A Login operation.
 * Validation removed to allow testing with missing credentials
 * to see actual IRS error responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    /**
     * Electronic Transmitter Identification Number
     */
    private String etin;

    /**
     * Username for A2A authentication
     */
    private String username;

    /**
     * Password for A2A authentication
     */
    private String password;

    /**
     * Whether to use production mode (true) or test mode (false)
     */
    private boolean productionMode = false;
}
