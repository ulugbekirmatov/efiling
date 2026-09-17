package com.irs.mef.controller;

import com.irs.mef.config.MefSdkConfig;
import com.irs.mef.dto.LoginResponse;
import com.irs.mef.dto.StatusResponse;
import com.irs.mef.dto.SubmitRequest;
import com.irs.mef.dto.SubmitResponse;
import com.irs.mef.exception.MefException;
import com.irs.mef.service.MefClientService;
import com.irs.mef.service.StatusService;
import com.irs.mef.service.SubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for submission operations.
 */
@RestController
@RequestMapping("/mef/submissions")
@Slf4j
@RequiredArgsConstructor
public class SubmissionController {

    /** IRS ATS scenario 1 (Orchid Incorporated, Q1 2026), relative to the repo root. */
    private static final String SCENARIO_1_RETURN_XML =
            "test-scenarios/941-scenario-1-orchid-q1-2026/Return941-Scenario1.xml";

    private final SubmissionService submissionService;
    private final StatusService statusService;
    private final MefClientService mefClientService;
    private final MefSdkConfig mefSdkConfig;

    /**
     * Submit tax returns to IRS MeF.
     *
     * POST /api/mef/submissions/submit
     *
     * Request body:
     * {
     *   "submissionId": "SUB123456",
     *   "submissionFilePath": "/path/to/submission.zip",
     *   "productionMode": false,
     *   "manifest": "...",
     *   "metadata": "..."
     * }
     *
     * @param request Submission request with file path and metadata
     * @return Submission response with status and message ID
     */
    @PostMapping("/submit")
    public ResponseEntity<SubmitResponse> submitSubmission(
            @Valid @RequestBody SubmitRequest request) {
        log.info("Submit request received for submission ID: {}", request.getSubmissionId());

        SubmitResponse response = submissionService.submitSubmission(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get status of a specific submission.
     *
     * GET /api/mef/submissions/{submissionId}/status
     *
     * @param submissionId Submission ID to query
     * @return Status response
     */
    @GetMapping("/{submissionId}/status")
    public ResponseEntity<StatusResponse> getSubmissionStatus(
            @PathVariable String submissionId) {
        log.info("Status query received for submission ID: {}", submissionId);

        StatusResponse response = statusService.getSubmissionStatus(submissionId);

        return ResponseEntity.ok(response);
    }

    /**
     * Get status of all new submissions.
     *
     * GET /api/mef/submissions/status/new
     *
     * @return List of status responses for new submissions
     */
    @GetMapping("/status/new")
    public ResponseEntity<List<StatusResponse>> getNewSubmissionsStatus() {
        log.info("New submissions status query received");

        List<StatusResponse> responses = statusService.getNewSubmissionsStatus();

        return ResponseEntity.ok(responses);
    }

    /**
     * Create a submission archive from files (helper endpoint).
     *
     * POST /api/mef/submissions/archive/create
     *
     * This is a helper endpoint to package submission files into the required ZIP format.
     *
     * Request parameters:
     * - submissionId: Submission identifier
     * - returnXmlPath: Path to return XML file
     * - attachments: Optional comma-separated attachment file paths
     *
     * @param submissionId Submission identifier
     * @param returnXmlPath Path to return XML file
     * @param attachments Optional attachment file paths
     * @return Path to created archive file
     */
    @PostMapping("/archive/create")
    public ResponseEntity<String> createSubmissionArchive(
            @RequestParam String submissionId,
            @RequestParam String returnXmlPath,
            @RequestParam(required = false) String attachments) {
        log.info("Create archive request received for submission ID: {}", submissionId);

        String[] attachmentArray = attachments != null ?
                attachments.split(",") : new String[0];

        String archivePath = submissionService.createSubmissionArchive(
                submissionId, returnXmlPath, attachmentArray);

        return ResponseEntity.ok(archivePath);
    }

    /**
     * Test Form 941 submission using Scenario 1 test data.
     *
     * GET /api/mef/submissions/test/form941
     *
     * This endpoint uses the pre-configured Form 941 test scenario data:
     * - Business: Orchid Incorporated
     * - EIN: 003000004
     * - Quarter: Q1 2026 (January-March)
     * - Test XML: test-scenarios/941-scenario-1-orchid-q1-2026/Return941-Scenario1.xml
     *
     * The submission ID is automatically generated with correct format:
     * [EFIN (6 digits)][Processing Date (7 digits - YYYYmDD)][Suffix (7 chars)]
     *
     * This endpoint automatically handles login if not already logged in,
     * making it self-contained for testing and debugging.
     *
     * @return Submission response with deposit ID and details
     */
    @GetMapping("/test/form941")
    public ResponseEntity<SubmitResponse> testForm941Submission() {
        log.info("Test Form 941 submission request received");

        // Check if logged in, if not, login first
        if (!mefClientService.isLoggedIn()) {
            log.info("Not logged in, performing login first...");
            try {
                LoginResponse loginResponse = mefClientService.login();
                log.info("Login successful. Session ID: {}", loginResponse.getSessionId());
            } catch (Exception e) {
                log.error("Login failed", e);
                throw new MefException("LOGIN_FAILED",
                    "Failed to login before submission",
                    e.getMessage(), e);
            }
        } else {
            log.info("Already logged in with session ID: {}", mefClientService.getCurrentSessionId());
        }

        // Generate valid submission ID with semantic structure
        // IRS requires: [EFIN (6)][Processing Date (7 - YYYYmDD)][Suffix (7)]
        // CRITICAL: Use CURRENT YEAR (processing year), not tax period year

        // Get EFIN from configuration (6 digits)
        String efin = mefSdkConfig.getAuthentication().getEfin();
        if (efin == null || efin.isEmpty()) {
            throw new MefException("CONFIG_ERROR",
                "EFIN not configured",
                "Please set MEF_EFIN environment variable");
        }

        // Validate EFIN is 6 digits
        if (!efin.matches("\\d{6}")) {
            throw new MefException("CONFIG_ERROR",
                "Invalid EFIN format",
                "EFIN must be exactly 6 digits, got: " + efin);
        }

        log.info("Using EFIN {} from configuration", efin);

        // Generate processing date using SDK's actual format: yyyyDDD (day-of-year)
        // DISCOVERED: MeF SDK SubmissionID class uses SimpleDateFormat("yyyyDDD")
        // Format: yyyy (4 digits) + DDD (3 digits, day of year 001-366)
        // Examples:
        //   - January 1, 2025: 2025001 (day 1)
        //   - March 31, 2025: 2025090 (day 90)
        //   - December 10, 2025: 2025344 (day 344)
        //   - December 31, 2025: 2025365 (day 365)
        // This format works for ALL dates and produces exactly 7 digits!
        java.time.LocalDate now = java.time.LocalDate.now();
        int dayOfYear = now.getDayOfYear();  // 1-366
        String processingDate = String.format("%d%03d",
            now.getYear(), dayOfYear);  // yyyyDDD (always 7 digits)

        // Generate UNIQUE suffix (7 lowercase alphanumeric characters)
        // Using timestamp-based suffix for uniqueness as per SUBMISSION_ID_FORMAT.md
        String suffix = String.valueOf(System.currentTimeMillis()).substring(6, 13);

        // Combine: [EFIN (6)][Processing Date (7)][Suffix (7)]
        String submissionId = efin + processingDate + suffix;

        // Validate submission ID format
        if (!submissionId.matches("[0-9]{13}[a-z0-9]{7}")) {
            throw new MefException("VALIDATION_ERROR",
                "Invalid submission ID format",
                "Expected format: [0-9]{13}[a-z0-9]{7}, got: " + submissionId);
        }

        log.info("Generated submission ID: {} (EFIN: {}, Processing Date: {}, Suffix: {})",
                submissionId, efin, processingDate, suffix);

        // Build submission request with test scenario data
        SubmitRequest request = new SubmitRequest();
        request.setSubmissionId(submissionId);

        // Scenario XML lives in the repo, one level above the Maven module (user.dir is the module when run via mvn).
        java.nio.file.Path moduleDir = java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        java.nio.file.Path repoRoot = moduleDir.getParent() != null ? moduleDir.getParent() : moduleDir;
        java.nio.file.Path scenarioXml = repoRoot.resolve(SCENARIO_1_RETURN_XML);
        if (!java.nio.file.Files.exists(scenarioXml)) {
            throw new MefException("CONFIG_ERROR",
                "Test scenario XML not found",
                "Expected " + scenarioXml + " (run from the mef-spring-boot-integration module directory)");
        }
        request.setSubmissionFilePath(scenarioXml.toString());

        // Production mode (false = ATS test environment)
        request.setProductionMode(false);

        // Get ETIN for manifest (transmitter who is submitting)
        String etin = mefSdkConfig.getAuthentication().getEtin();
        if (etin == null || etin.isEmpty()) {
            throw new MefException("CONFIG_ERROR",
                "ETIN not configured",
                "Please set MEF_ETIN environment variable");
        }

        request.setEfin(efin);
        request.setTin("003000004");  // EIN from Return941-Scenario1.xml

        // Tax period: Q1 2026 (January 1 - March 31, 2026)
        request.setTaxPeriodBegin(java.time.LocalDate.of(2026, 1, 1));
        request.setTaxPeriodEnd(java.time.LocalDate.of(2026, 3, 31));

        log.info("Test submission configuration:");
        log.info("  - Submission ID: {} (EFIN: {}, Date: {}, Suffix: {})",
                submissionId, efin, processingDate, suffix);
        log.info("  - XML File: {}", request.getSubmissionFilePath());
        log.info("  - Environment: ATS (test)");
        log.info("  - Manifest ETIN (Transmitter): {}", etin);
        log.info("  - TIN (EIN): {}", request.getTin());
        log.info("  - Tax Period: {} to {}", request.getTaxPeriodBegin(), request.getTaxPeriodEnd());

        // Submit to IRS MeF
        SubmitResponse response = submissionService.submitSubmission(request);

        log.info("Test submission completed:");
        log.info("  - Submission ID: {}", response.getSubmissionId());
        log.info("  - Deposit ID: {}", response.getDepositId());
        log.info("  - Status: {}", response.getStatus());
        log.info("  - Message: {}", response.getMessage());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
