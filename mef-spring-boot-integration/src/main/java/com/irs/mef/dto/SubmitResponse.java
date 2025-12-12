package com.irs.mef.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for submission operations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmitResponse {

    /**
     * Submission ID
     */
    private String submissionId;

    /**
     * IRS-assigned deposit ID (returned after successful submission)
     */
    private String depositId;

    /**
     * IRS-assigned message ID
     */
    private String messageId;

    /**
     * Submission status
     */
    private String status;

    /**
     * Timestamp of submission
     */
    private String timestamp;

    /**
     * Receipt or confirmation message
     */
    private String message;

    /**
     * Error message if submission failed
     */
    private String errorMessage;

    /**
     * Whether submission was accepted
     */
    private boolean accepted;
}
