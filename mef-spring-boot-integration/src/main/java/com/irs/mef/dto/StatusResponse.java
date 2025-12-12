package com.irs.mef.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for submission status queries.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusResponse {

    /**
     * Submission ID
     */
    private String submissionId;

    /**
     * Current status of the submission
     * (e.g., "Accepted", "Rejected", "Pending", "Processing")
     */
    private String status;

    /**
     * Status code
     */
    private String statusCode;

    /**
     * Timestamp of status
     */
    private String timestamp;

    /**
     * Status description
     */
    private String description;

    /**
     * Additional status details
     */
    private String details;

    /**
     * Whether acknowledgment is available
     */
    private boolean ackAvailable;
}
