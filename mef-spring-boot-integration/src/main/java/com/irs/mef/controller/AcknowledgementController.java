package com.irs.mef.controller;

import com.irs.mef.dto.AckResponse;
import com.irs.mef.service.AcknowledgementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for acknowledgment operations.
 */
@RestController
@RequestMapping("/mef/acknowledgments")
@Slf4j
@RequiredArgsConstructor
public class AcknowledgementController {

    private final AcknowledgementService acknowledgementService;

    /**
     * Get a specific acknowledgment by ID.
     *
     * GET /api/mef/acknowledgments/{ackId}
     *
     * @param ackId Acknowledgment ID
     * @return Acknowledgment response with details and file path
     */
    @GetMapping("/{ackId}")
    public ResponseEntity<AckResponse> getAcknowledgment(@PathVariable String ackId) {
        log.info("Get acknowledgment request received for ID: {}", ackId);

        AckResponse response = acknowledgementService.getAcknowledgment(ackId);

        return ResponseEntity.ok(response);
    }

    /**
     * Get all new acknowledgments (those not yet retrieved).
     *
     * GET /api/mef/acknowledgments/new
     *
     * @return List of acknowledgment responses
     */
    @GetMapping("/new")
    public ResponseEntity<AckResponse.AckListResponse> getNewAcknowledgments() {
        log.info("Get new acknowledgments request received");

        AckResponse.AckListResponse response = acknowledgementService.getNewAcknowledgments();

        return ResponseEntity.ok(response);
    }

    /**
     * Get acknowledgments for a specific submission.
     *
     * GET /api/mef/acknowledgments/submission/{submissionId}
     *
     * @param submissionId Submission ID
     * @return List of acknowledgments for the submission
     */
    @GetMapping("/submission/{submissionId}")
    public ResponseEntity<List<AckResponse>> getAcknowledgmentsBySubmission(
            @PathVariable String submissionId) {
        log.info("Get acknowledgments request received for submission ID: {}", submissionId);

        List<AckResponse> responses = acknowledgementService.getAcknowledgmentsBySubmission(submissionId);

        return ResponseEntity.ok(responses);
    }
}
