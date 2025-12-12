package com.irs.mef.controller;

import com.irs.mef.dto.CertificateTestResponse;
import com.irs.mef.dto.LoginResponse;
import com.irs.mef.service.MefClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for MeF authentication operations (login/logout).
 */
@RestController
@RequestMapping("/mef/auth")
@Slf4j
@RequiredArgsConstructor
public class MefAuthController {

    private final MefClientService mefClientService;

    /**
     * Login to IRS MeF A2A services.
     *
     * GET /api/mef/auth/login
     *
     * Login parameters are read from environment variables:
     * - MEF_ETIN: Electronic Transmitter Identification Number
     * - MEF_ASID: Application System ID
     * - mef.sdk.environment: ATS (test) or PRD (production)
     *
     * @return Login response with SAML assertion and session info
     */
    @GetMapping("/login")
    public ResponseEntity<LoginResponse> login() {
        log.info("Login request received");

        LoginResponse response = mefClientService.login();

        return ResponseEntity.ok(response);
    }

    /**
     * Logout from IRS MeF A2A services.
     *
     * GET /api/mef/auth/logout
     *
     * @return Logout confirmation
     */
    @GetMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        log.info("Logout request received");

        boolean success = mefClientService.logout();

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ? "Logged out successfully" : "No active session");

        return ResponseEntity.ok(response);
    }

    /**
     * Check current session status.
     *
     * GET /api/mef/auth/status
     *
     * @return Session status information including IRS SAML Assertion ID
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSessionStatus() {
        log.info("Session status check requested");

        boolean isLoggedIn = mefClientService.isLoggedIn();
        String sessionId = isLoggedIn ? mefClientService.getCurrentSessionId() : null;

        Map<String, Object> response = new HashMap<>();
        response.put("loggedIn", isLoggedIn);
        response.put("samlAssertionId", sessionId);  // This is the real session ID from IRS SAML token
        response.put("sessionId", sessionId);  // Keep for backward compatibility

        if (isLoggedIn) {
            response.put("sessionType", "IRS SAML Assertion");
            response.put("message", "Active session with IRS MeF using SAML authentication");
        } else {
            response.put("message", "No active session");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Test certificate loading and validation (layered approach).
     *
     * GET /api/mef/auth/test-certificate
     *
     * Query parameters:
     * - fullAuthTest (optional, default: false): Whether to perform full authentication test
     *
     * This endpoint performs layered certificate validation:
     * - Level 1: Keystore loading using SDK's KeyStoreUtil
     * - Level 2: Certificate details extraction
     * - Level 3: Full authentication with IRS (optional)
     *
     * @param fullAuthTest Whether to perform full authentication test (Level 3)
     * @return Certificate test response with results from each layer
     */
    @GetMapping("/test-certificate")
    public ResponseEntity<CertificateTestResponse> testCertificate(
            @RequestParam(name = "fullAuthTest", required = false, defaultValue = "false") boolean fullAuthTest) {
        log.info("Certificate test requested (fullAuthTest: {})", fullAuthTest);

        CertificateTestResponse response = mefClientService.testCertificate(fullAuthTest);

        // Return appropriate HTTP status based on test results
        HttpStatus status = response.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;

        return ResponseEntity.status(status).body(response);
    }
}
