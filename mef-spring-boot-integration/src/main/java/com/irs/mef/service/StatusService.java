package com.irs.mef.service;

import com.irs.mef.config.MefSdkConfig;
import com.irs.mef.dto.StatusResponse;
import com.irs.mef.exception.MefException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.xml.datatype.XMLGregorianCalendar;

/**
 * Service for querying submission status from IRS MeF.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StatusService {

    private final MefClientService mefClientService;
    private final MefSdkConfig mefConfig;
    private final RetryTemplate mefRetryTemplate;

    /**
     * Get status of a specific submission.
     * Uses reflection to load SDK classes to avoid Java module access issues.
     *
     * @param submissionId Submission ID to query
     * @return Status response
     */
    public StatusResponse getSubmissionStatus(String submissionId) {
        log.info("Querying status for submission ID: {}", submissionId);

        // Verify logged in
        if (!mefClientService.isLoggedIn()) {
            throw new MefException("NOT_LOGGED_IN", "Not logged in to IRS MeF",
                    "Please login before querying status");
        }

        // Wrap with retry logic for handling session limits and transient errors
        return mefRetryTemplate.execute(context -> {
            try {
                // Get ServiceContext from login session
                Object serviceContext = mefClientService.getCurrentServiceContext();

                // Load SDK classes via reflection
                Class<?> serviceContextClass = Class.forName("gov.irs.mef.services.ServiceContext");
                Class<?> getSubmissionStatusClientClass = Class.forName("gov.irs.mef.services.transmitter.GetSubmissionStatusClient");

                // Create GetSubmissionStatusClient via reflection
                log.info("Creating GetSubmissionStatusClient for submission ID: {}", submissionId);
                Object client = getSubmissionStatusClientClass.getDeclaredConstructor().newInstance();

                // Invoke GetSubmissionStatus via reflection
                log.info("Invoking GetSubmissionStatus service...");
                Method invokeMethod = getSubmissionStatusClientClass.getMethod("invoke", serviceContextClass, String.class);
                Object result = invokeMethod.invoke(client, serviceContext, submissionId);

                // Extract status records from result via reflection
                Class<?> getSubmissionStatusResultClass = Class.forName("gov.irs.mef.services.transmitter.GetSubmissionStatusResult");
                Method getStatusRecordListMethod = getSubmissionStatusResultClass.getMethod("getStatusRecordList");
                Object statusRecordList = getStatusRecordListMethod.invoke(result);

                if (statusRecordList == null) {
                    log.warn("No status records found for submission ID: {}", submissionId);
                    throw new MefException("NO_STATUS_FOUND",
                            "No status records found",
                            "The IRS has no status information for submission ID: " + submissionId);
                }

                // Get status records list via reflection
                Class<?> statusRecordListClass = Class.forName("gov.irs.mef.StatusRecordList");
                Method getStatusRecordsMethod = statusRecordListClass.getMethod("getStatusRecords");
                List<?> statusRecords = (List<?>) getStatusRecordsMethod.invoke(statusRecordList);

                if (statusRecords == null || statusRecords.isEmpty()) {
                    log.warn("No status records found for submission ID: {}", submissionId);
                    throw new MefException("NO_STATUS_FOUND",
                            "No status records found",
                            "The IRS has no status information for submission ID: " + submissionId);
                }

                // Get first status record via reflection
                Object statusRecord = statusRecords.get(0);
                Class<?> statusRecordClass = statusRecord.getClass();

                String status = (String) statusRecordClass.getMethod("getSubmissionStatusTxt").invoke(statusRecord);
                XMLGregorianCalendar statusDate = (XMLGregorianCalendar) statusRecordClass.getMethod("getSubmsnStatusAcknowledgementDt").invoke(statusRecord);
                String disclaimer = (String) statusRecordClass.getMethod("getDisclaimerTxt").invoke(statusRecord);
                String recordSubmissionId = (String) statusRecordClass.getMethod("getSubmissionId").invoke(statusRecord);

                log.info("Status query successful for submission ID: {}, Status: {}, Date: {}",
                        submissionId, status, statusDate);

                return StatusResponse.builder()
                        .submissionId(recordSubmissionId)
                        .status(status)
                        .statusCode(null) // Not provided by SDK
                        .timestamp(statusDate != null ? statusDate.toString() : LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                        .description(disclaimer != null ? disclaimer : "Status retrieved successfully")
                        .ackAvailable(status != null && (status.contains("Accept") || status.contains("Reject")))
                        .build();

            } catch (MefException e) {
                // Don't retry MefException (non-retryable application errors)
                throw e;
            } catch (Exception e) {
                // Check if this is a session limit error (retryable)
                String errorMsg = e.getMessage() != null ? e.getMessage() : "";
                Throwable cause = e.getCause();
                String causeMsg = cause != null && cause.getMessage() != null ? cause.getMessage() : "";

                if (errorMsg.contains("Session limit") || causeMsg.contains("Session limit") ||
                        errorMsg.contains("Too many concurrent sessions") || causeMsg.contains("Too many concurrent sessions") ||
                        errorMsg.contains("concurrent session") || causeMsg.contains("concurrent session")) {
                    log.warn("IRS session limit reached for status query (attempt {}). Will retry...",
                            context.getRetryCount() + 1);
                    // Re-throw wrapped in RuntimeException to trigger retry
                    throw new RuntimeException("IRS session limit - retryable", e);
                }

                // Check if this is an SDK exception
                String exClassName = e.getClass().getName();
                if (exClassName.contains("gov.irs.mef.exception")) {
                    log.error("MeF SDK error while querying status for submission ID: {}", submissionId, e);
                    throw new MefException("STATUS_QUERY_FAILED",
                            "MeF SDK error: " + e.getMessage(), e.getMessage(), e);
                }

                log.error("Failed to query status for submission ID: {}", submissionId, e);
                throw new MefException("STATUS_QUERY_FAILED",
                        "Failed to query submission status", e.getMessage(), e);
            }
        });
    }

    /**
     * Get status of all new submissions (those not yet retrieved).
     *
     * @return List of status responses for new submissions
     */
    public List<StatusResponse> getNewSubmissionsStatus() {
        log.info("Querying status for all new submissions");

        // Verify logged in
        if (!mefClientService.isLoggedIn()) {
            throw new MefException("NOT_LOGGED_IN", "Not logged in to IRS MeF",
                    "Please login before querying status");
        }

        try {
            // TODO: Implement actual MeF SDK GetNewSubmissionsStatus call
            // Example code structure:
            //
            // GetNewSubmissionsStatusServiceType statusService = new GetNewSubmissionsStatusServiceType();
            //
            // // Set SAML assertion
            // String samlAssertion = mefClientService.getCurrentSamlAssertion();
            // Map<String, Object> requestContext = ((BindingProvider) statusService).getRequestContext();
            // requestContext.put("saml.assertion", samlAssertion);
            //
            // // Invoke service
            // GetNewSubmissionsStatus request = new GetNewSubmissionsStatus();
            // GetNewSubmissionsStatusResponse response = statusService.getNewSubmissionsStatus(
            //     request,
            //     mefConfig.getEnvironment().equalsIgnoreCase("PRD")
            // );
            //
            // // Process response
            // List<SubmissionStatus> statusList = response.getSubmissionStatusList();
            // List<StatusResponse> results = new ArrayList<>();
            // for (SubmissionStatus status : statusList) {
            //     results.add(StatusResponse.builder()
            //         .submissionId(status.getSubmissionId())
            //         .status(status.getStatus())
            //         .statusCode(status.getStatusCode())
            //         .timestamp(status.getTimestamp())
            //         .ackAvailable(status.isAckAvailable())
            //         .build());
            // }

            // PLACEHOLDER RESPONSE - Replace with actual SDK call
            List<StatusResponse> results = new ArrayList<>();
            log.info("Found {} new submission statuses", results.size());
            return results;

        } catch (Exception e) {
            log.error("Failed to query new submissions status", e);
            throw new MefException("STATUS_QUERY_FAILED",
                    "Failed to query new submissions status", e.getMessage(), e);
        }
    }

    /**
     * Get certificate file from configuration for SDK client initialization.
     *
     * @return Certificate File object
     */
    private File getCertificateFile() {
        if (!mefConfig.isCertificateConfigured()) {
            throw new MefException("CERTIFICATE_NOT_CONFIGURED",
                    "Certificate not configured",
                    "Please configure keystore path and password in application.yml");
        }

        String keystorePath = mefConfig.getCertificate().getKeystorePath();
        File certificateFile = new File(keystorePath);

        if (!certificateFile.exists() || !certificateFile.isFile()) {
            throw new MefException("KEYSTORE_NOT_FOUND",
                    "Keystore file not found",
                    "Keystore file does not exist at: " + keystorePath);
        }

        return certificateFile;
    }
}
