package com.irs.mef.service;

import com.irs.mef.config.MefSdkConfig;
import com.irs.mef.dto.AckResponse;
import com.irs.mef.exception.MefException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.xml.datatype.XMLGregorianCalendar;

/**
 * Service for retrieving acknowledgments from IRS MeF.
 *
 * <p>Provides three acknowledgment retrieval methods:</p>
 * <ol>
 *   <li>{@link #getAcknowledgment(String)} - Get single acknowledgment by submission ID</li>
 *   <li>{@link #getNewAcknowledgments()} - Get all new (unretrieved) acknowledgments</li>
 *   <li>{@link #getAcknowledgmentsBySubmission(String)} - Filter new acks by submission ID</li>
 * </ol>
 *
 * <p><b>IRS Data Caveats:</b></p>
 * <ul>
 *   <li>EFIN may be "999999" when IRS doesn't have EFIN on record</li>
 *   <li>TIN may be null - IRS doesn't always populate this field</li>
 *   <li>Financial amounts may be null - depends on form type</li>
 *   <li>EIN is the most reliable tax identifier for employer returns</li>
 * </ul>
 *
 * <p><b>Implementation Notes:</b></p>
 * <ul>
 *   <li>Uses reflection to load SDK classes to avoid Java module access issues</li>
 *   <li>Includes retry logic for IRS session limit handling</li>
 *   <li>Comprehensive error handling for ServiceException and ToolkitException</li>
 *   <li>All methods require active MeF login session</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AcknowledgementService {

    private final MefClientService mefClientService;
    private final MefSdkConfig mefConfig;
    private final RetryTemplate mefRetryTemplate;

    /**
     * Get a specific acknowledgment by submission ID.
     *
     * <p>This method retrieves a single acknowledgment from IRS MeF using the GetAck service.
     * The acknowledgment ID parameter is actually the submission ID (20-character format).</p>
     *
     * <p><b>Important:</b> This method uses the GetAck SDK client which requires the submission ID,
     * not a separate acknowledgment ID. The submission ID format is: [0-9]{13}[a-z0-9]{7}</p>
     *
     * @param ackId Submission ID (20-char format, also serves as acknowledgment identifier)
     * @return Acknowledgment response with full details including tax IDs, financial amounts, etc.
     * @throws MefException if not logged in, acknowledgment not found, or IRS service error
     */
    public AckResponse getAcknowledgment(String ackId) {
        log.info("Retrieving acknowledgment by submission ID: {}", ackId);

        // Verify logged in
        if (!mefClientService.isLoggedIn()) {
            throw new MefException("NOT_LOGGED_IN", "Not logged in to IRS MeF",
                    "Please login before retrieving acknowledgments");
        }

        // Wrap with retry logic for handling session limits and transient errors
        return mefRetryTemplate.execute(context -> {
            try {
                // Get ServiceContext from login session
                Object serviceContext = mefClientService.getCurrentServiceContext();

                // Load SDK classes via reflection
                Class<?> serviceContextClass = Class.forName("gov.irs.mef.services.ServiceContext");
                Class<?> getAckClientClass = Class.forName("gov.irs.mef.services.transmitter.GetAckClient");

                // Create GetAckClient via reflection
                log.info("Creating GetAckClient to retrieve acknowledgment for submission ID: {}", ackId);
                Object client = getAckClientClass.getDeclaredConstructor().newInstance();

                // Invoke GetAck service with submission ID via reflection
                log.info("Invoking GetAck service with submission ID: {}", ackId);
                Method invokeMethod = getAckClientClass.getMethod("invoke", serviceContextClass, String.class);
                Object result = invokeMethod.invoke(client, serviceContext, ackId);

                // Extract acknowledgment list from result via reflection
                Class<?> getAckResultClass = Class.forName("gov.irs.mef.services.transmitter.GetAckResult");
                Method getAckListMethod = getAckResultClass.getMethod("getAcknowledgementList");
                Object ackList = getAckListMethod.invoke(result);

                if (ackList == null) {
                    log.warn("No acknowledgment found for submission ID: {}", ackId);
                    throw new MefException("ACK_NOT_FOUND",
                            "Acknowledgment not found",
                            "The IRS has no acknowledgment for submission ID: " + ackId);
                }

                // Get acknowledgements list via reflection
                Class<?> ackListClass = Class.forName("gov.irs.mef.AcknowledgementList");
                Method getAcknowledgementsMethod = ackListClass.getMethod("getAcknowledgements");
                List<?> acks = (List<?>) getAcknowledgementsMethod.invoke(ackList);

                if (acks == null || acks.isEmpty()) {
                    log.warn("No acknowledgment found for submission ID: {}", ackId);
                    throw new MefException("ACK_NOT_FOUND",
                            "Acknowledgment not found",
                            "The IRS has no acknowledgment for submission ID: " + ackId);
                }

                // GetAck returns a single acknowledgment
                Object ack = acks.get(0);

                // Log if multiple acknowledgments returned (shouldn't happen, but defensive)
                if (acks.size() > 1) {
                    log.warn("Multiple acknowledgments returned for submission ID: {} (count: {})",
                            ackId, acks.size());
                }

                // Build response using helper method
                AckResponse response = buildAckResponseReflection(ack);

                log.info("Acknowledgment retrieved successfully for submission ID: {}", ackId);

                return response;

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
                    log.warn("IRS session limit reached for acknowledgment retrieval (attempt {}). Will retry...",
                            context.getRetryCount() + 1);
                    // Re-throw wrapped in RuntimeException to trigger retry
                    throw new RuntimeException("IRS session limit - retryable", e);
                }

                // Check if this is an SDK exception
                String exClassName = e.getClass().getName();
                if (exClassName.contains("gov.irs.mef.exception")) {
                    log.error("MeF SDK error while retrieving acknowledgment for submission ID: {}", ackId, e);
                    throw new MefException("ACK_RETRIEVAL_FAILED",
                            "MeF SDK error: " + e.getMessage(), e.getMessage(), e);
                }

                log.error("Failed to retrieve acknowledgment for submission ID: {}", ackId, e);
                throw new MefException("ACK_RETRIEVAL_FAILED",
                        "Failed to retrieve acknowledgment", e.getMessage(), e);
            }
        });
    }

    /**
     * Get all new acknowledgments (those not yet retrieved).
     * Uses reflection to load SDK classes.
     *
     * @return List of acknowledgment responses
     */
    public AckResponse.AckListResponse getNewAcknowledgments() {
        log.info("Retrieving all new acknowledgments");

        // Verify logged in
        if (!mefClientService.isLoggedIn()) {
            throw new MefException("NOT_LOGGED_IN", "Not logged in to IRS MeF",
                    "Please login before retrieving acknowledgments");
        }

        // Wrap with retry logic for handling session limits and transient errors
        return mefRetryTemplate.execute(context -> {
            try {
                // Get ServiceContext from login session
                Object serviceContext = mefClientService.getCurrentServiceContext();

                // Load SDK classes via reflection
                Class<?> serviceContextClass = Class.forName("gov.irs.mef.services.ServiceContext");
                Class<?> getNewAcksClientClass = Class.forName("gov.irs.mef.services.transmitter.GetNewAcksClient");

                // Create GetNewAcksClient via reflection
                log.info("Creating GetNewAcksClient to retrieve new acknowledgments");
                Object client = getNewAcksClientClass.getDeclaredConstructor().newInstance();

                // Invoke GetNewAcks (maxCount = 100, no category/government filters) via reflection
                Integer maxCount = 100;
                log.info("Invoking GetNewAcks service with maxCount: {}", maxCount);
                Method invokeMethod = getNewAcksClientClass.getMethod("invoke", serviceContextClass, Integer.class);
                Object result = invokeMethod.invoke(client, serviceContext, maxCount);

                // Extract acknowledgment list from result via reflection
                Class<?> getNewAcksResultClass = Class.forName("gov.irs.mef.services.transmitter.GetNewAcksResult");
                Method getAckListMethod = getNewAcksResultClass.getMethod("getAcknowledgementList");
                Object ackList = getAckListMethod.invoke(result);

                List<AckResponse> results = new ArrayList<>();

                if (ackList != null) {
                    // Get acknowledgements list via reflection
                    Class<?> ackListClass = Class.forName("gov.irs.mef.AcknowledgementList");
                    Method getAcknowledgementsMethod = ackListClass.getMethod("getAcknowledgements");
                    List<?> acks = (List<?>) getAcknowledgementsMethod.invoke(ackList);

                    if (acks != null && !acks.isEmpty()) {
                        log.info("Processing {} acknowledgments from IRS", acks.size());

                        for (Object ack : acks) {
                            AckResponse ackResponse = buildAckResponseReflection(ack);
                            results.add(ackResponse);
                        }
                    }
                } else {
                    log.info("No new acknowledgments available from IRS");
                }

                // Check if more available via reflection
                Method isMoreAvailableIndMethod = getNewAcksResultClass.getMethod("isMoreAvailableInd");
                boolean moreAvailable = (Boolean) isMoreAvailableIndMethod.invoke(result);
                log.info("Retrieved {} new acknowledgments, More available: {}", results.size(), moreAvailable);

                return AckResponse.AckListResponse.builder()
                        .acknowledgments(results)
                        .totalCount(results.size())
                        .message(moreAvailable ?
                                "Successfully retrieved " + results.size() + " acknowledgments (more available)" :
                                "Successfully retrieved all " + results.size() + " new acknowledgments")
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
                    log.warn("IRS session limit reached for acknowledgment retrieval (attempt {}). Will retry...",
                            context.getRetryCount() + 1);
                    // Re-throw wrapped in RuntimeException to trigger retry
                    throw new RuntimeException("IRS session limit - retryable", e);
                }

                // Check if this is an SDK exception
                String exClassName = e.getClass().getName();
                if (exClassName.contains("gov.irs.mef.exception")) {
                    log.error("MeF SDK error while retrieving new acknowledgments", e);
                    throw new MefException("ACK_RETRIEVAL_FAILED",
                            "MeF SDK error: " + e.getMessage(), e.getMessage(), e);
                }

                log.error("Failed to retrieve new acknowledgments", e);
                throw new MefException("ACK_RETRIEVAL_FAILED",
                        "Failed to retrieve new acknowledgments", e.getMessage(), e);
            }
        });
    }

    /**
     * Get acknowledgments for a specific submission.
     * Uses reflection to load SDK classes.
     *
     * Implementation Note: The IRS MeF SDK does not provide a direct method to query
     * acknowledgments by submission ID. This method retrieves all new acknowledgments
     * and filters them by the specified submission ID.
     *
     * @param submissionId Submission ID to filter acknowledgments
     * @return List of acknowledgments for the submission
     */
    public List<AckResponse> getAcknowledgmentsBySubmission(String submissionId) {
        log.info("Retrieving acknowledgments for submission ID: {}", submissionId);

        // Verify logged in
        if (!mefClientService.isLoggedIn()) {
            throw new MefException("NOT_LOGGED_IN", "Not logged in to IRS MeF",
                    "Please login before retrieving acknowledgments");
        }

        // Wrap with retry logic for handling session limits and transient errors
        return mefRetryTemplate.execute(context -> {
            try {
                // Get ServiceContext from login session
                Object serviceContext = mefClientService.getCurrentServiceContext();

                // Load SDK classes via reflection
                Class<?> serviceContextClass = Class.forName("gov.irs.mef.services.ServiceContext");
                Class<?> getNewAcksClientClass = Class.forName("gov.irs.mef.services.transmitter.GetNewAcksClient");

                // Create GetNewAcksClient via reflection
                log.info("Creating GetNewAcksClient to retrieve acknowledgments for submission ID: {}", submissionId);
                Object client = getNewAcksClientClass.getDeclaredConstructor().newInstance();

                // Invoke GetNewAcks with higher maxCount to ensure we get all acknowledgments via reflection
                Integer maxCount = 100;
                log.info("Invoking GetNewAcks service with maxCount: {} to search for submission: {}",
                        maxCount, submissionId);
                Method invokeMethod = getNewAcksClientClass.getMethod("invoke", serviceContextClass, Integer.class);
                Object result = invokeMethod.invoke(client, serviceContext, maxCount);

                // Extract acknowledgment list from result via reflection
                Class<?> getNewAcksResultClass = Class.forName("gov.irs.mef.services.transmitter.GetNewAcksResult");
                Method getAckListMethod = getNewAcksResultClass.getMethod("getAcknowledgementList");
                Object ackList = getAckListMethod.invoke(result);

                List<AckResponse> results = new ArrayList<>();

                if (ackList != null) {
                    // Get acknowledgements list via reflection
                    Class<?> ackListClass = Class.forName("gov.irs.mef.AcknowledgementList");
                    Method getAcknowledgementsMethod = ackListClass.getMethod("getAcknowledgements");
                    List<?> acks = (List<?>) getAcknowledgementsMethod.invoke(ackList);

                    if (acks != null && !acks.isEmpty()) {
                        log.info("Processing {} acknowledgments from IRS, filtering by submission ID: {}",
                                acks.size(), submissionId);

                        // Get method for submissionId
                        Class<?> ackClass = Class.forName("gov.irs.mef.AcknowledgementList$Acknowledgement");
                        Method getSubmissionIdMethod = ackClass.getMethod("getSubmissionId");

                        for (Object ack : acks) {
                            String ackSubmissionId = (String) getSubmissionIdMethod.invoke(ack);

                            // Filter: only include acknowledgments matching the requested submission ID
                            if (!submissionId.equals(ackSubmissionId)) {
                                continue;
                            }

                            AckResponse ackResponse = buildAckResponseReflection(ack);
                            results.add(ackResponse);

                            log.info("Found matching acknowledgment for SubmissionID: {}", ackSubmissionId);
                        }
                    }
                } else {
                    log.info("No acknowledgments available from IRS");
                }

                if (results.isEmpty()) {
                    log.info("No acknowledgments found for submission ID: {}", submissionId);
                } else {
                    log.info("Found {} acknowledgment(s) for submission ID: {}", results.size(), submissionId);
                }

                return results;

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
                    log.warn("IRS session limit reached for acknowledgment retrieval (attempt {}). Will retry...",
                            context.getRetryCount() + 1);
                    // Re-throw wrapped in RuntimeException to trigger retry
                    throw new RuntimeException("IRS session limit - retryable", e);
                }

                // Check if this is an SDK exception
                String exClassName = e.getClass().getName();
                if (exClassName.contains("gov.irs.mef.exception")) {
                    log.error("MeF SDK error while retrieving acknowledgments for submission: {}", submissionId, e);
                    throw new MefException("ACK_RETRIEVAL_FAILED",
                            "MeF SDK error: " + e.getMessage(), e.getMessage(), e);
                }

                log.error("Failed to retrieve acknowledgments for submission ID: {}", submissionId, e);
                throw new MefException("ACK_RETRIEVAL_FAILED",
                        "Failed to retrieve acknowledgments for submission", e.getMessage(), e);
            }
        });
    }

    /**
     * Build AckResponse DTO from SDK Acknowledgement object using reflection.
     *
     * <p>Extracts all available fields from the IRS acknowledgement including:</p>
     * <ul>
     *   <li>Tax identifiers (EFIN, EIN, TIN)</li>
     *   <li>Financial amounts (taxable income, total tax, balance due, refund)</li>
     *   <li>Form details (tax year, submission type, category)</li>
     *   <li>Timestamps (postmark, received date, tax period end)</li>
     *   <li>IRS identifiers (IRS submission ID, receipt ID)</li>
     *   <li>Validation errors and alerts</li>
     * </ul>
     *
     * <p><b>IRS Data Caveats:</b></p>
     * <ul>
     *   <li><b>EFIN = "999999"</b> - IRS placeholder when EFIN is unknown</li>
     *   <li><b>TIN = null</b> - IRS doesn't always populate TIN field</li>
     *   <li><b>Financial amounts = null</b> - Not all forms include financial data</li>
     * </ul>
     *
     * @param ack SDK Acknowledgement object from GetAckResult or GetNewAcksResult
     * @return Fully populated AckResponse DTO with all available fields
     */
    private AckResponse buildAckResponseReflection(Object ack) {
        try {
            Class<?> ackClass = ack.getClass();

            // Helper method to safely invoke getter
            java.util.function.Function<String, Object> getField = (methodName) -> {
                try {
                    Method m = ackClass.getMethod(methodName);
                    return m.invoke(ack);
                } catch (Exception e) {
                    return null;
                }
            };

            // ========== Extract Basic Fields ==========
            String submissionId = (String) getField.apply("getSubmissionId");
            String acceptanceStatus = (String) getField.apply("getAcceptanceStatusTxt");

            // ========== Extract Tax Identifiers ==========
            String efin = (String) getField.apply("getEFIN");
            String ein = (String) getField.apply("getEIN");
            String tin = (String) getField.apply("getTIN");

            // ========== Extract Timestamps ==========
            XMLGregorianCalendar statusDate = (XMLGregorianCalendar) getField.apply("getStatusDt");
            XMLGregorianCalendar postmarkTs = (XMLGregorianCalendar) getField.apply("getElectronicPostmarkTs");
            XMLGregorianCalendar receivedDt = (XMLGregorianCalendar) getField.apply("getIRSReceivedDt");
            XMLGregorianCalendar taxPeriodEndDt = (XMLGregorianCalendar) getField.apply("getTaxPeriodEndDt");

            // ========== Extract Financial Amounts (BigInteger → Long) ==========
            BigInteger taxableIncome = (BigInteger) getField.apply("getTaxableIncomeAmt");
            BigInteger totalTax = (BigInteger) getField.apply("getTotalTaxAmt");
            BigInteger balanceDue = (BigInteger) getField.apply("getBalanceDueAmt");
            BigInteger expectedRefund = (BigInteger) getField.apply("getExpectedRefundAmt");
            BigInteger netIncomeLoss = (BigInteger) getField.apply("getNetIncomeLossAmt");

            Long taxableIncomeAmt = taxableIncome != null ? taxableIncome.longValue() : null;
            Long totalTaxAmt = totalTax != null ? totalTax.longValue() : null;
            Long balanceDueAmt = balanceDue != null ? balanceDue.longValue() : null;
            Long expectedRefundAmt = expectedRefund != null ? expectedRefund.longValue() : null;
            Long netIncomeLossAmt = netIncomeLoss != null ? netIncomeLoss.longValue() : null;

            // ========== Extract Form Details ==========
            String taxYear = (String) getField.apply("getTaxYr");
            String submissionType = (String) getField.apply("getSubmissionTyp");
            String submissionCategoryCode = (String) getField.apply("getExtndSubmissionCategoryCd");

            // ========== Extract IRS Identifiers ==========
            String irsSubmissionId = (String) getField.apply("getIRSSubmissionId");
            String receiptId = (String) getField.apply("getReceiptId");

            // ========== Extract Additional Fields ==========
            String paymentRequestCode = (String) getField.apply("getPaymentRequestRcvdCd");

            // ========== Extract Validation Errors via reflection ==========
            Object errorList = getField.apply("getValidationErrorList");
            Object alertList = getField.apply("getValidationAlertList");

            boolean hasErrors = false;
            boolean hasAlerts = false;
            String errorDetails = null;

            if (errorList != null) {
                try {
                    Class<?> errorListClass = errorList.getClass();
                    Method getErrorGrpMethod = errorListClass.getMethod("getValidationErrorGrp");
                    List<?> errorGrps = (List<?>) getErrorGrpMethod.invoke(errorList);
                    hasErrors = (errorGrps != null && !errorGrps.isEmpty());

                    if (hasErrors) {
                        Method getErrorCntMethod = errorListClass.getMethod("getErrorCnt");
                        Object errorCnt = getErrorCntMethod.invoke(errorList);

                        StringBuilder errorBuilder = new StringBuilder();
                        errorBuilder.append("Validation errors (").append(errorCnt).append("):\n");

                        for (Object error : errorGrps) {
                            Class<?> errorGrpClass = error.getClass();
                            String ruleNum = (String) errorGrpClass.getMethod("getRuleNum").invoke(error);
                            String severityCd = (String) errorGrpClass.getMethod("getSeverityCd").invoke(error);
                            String errorMessage = (String) errorGrpClass.getMethod("getErrorMessageTxt").invoke(error);
                            String xpathContent = (String) errorGrpClass.getMethod("getXpathContentTxt").invoke(error);
                            String fieldValue = (String) errorGrpClass.getMethod("getFieldValueTxt").invoke(error);

                            log.error("VALIDATION ERROR for SubmissionID={}: Rule={}, Severity={}, Message={}",
                                    submissionId, ruleNum, severityCd, errorMessage);

                            errorBuilder.append("  - Rule ").append(ruleNum)
                                    .append(" [").append(severityCd).append("]: ")
                                    .append(errorMessage);
                            if (xpathContent != null) {
                                errorBuilder.append(" (XPath: ").append(xpathContent).append(")");
                            }
                            if (fieldValue != null) {
                                errorBuilder.append(" [Value: ").append(fieldValue).append("]");
                            }
                            errorBuilder.append("\n");
                        }
                        errorDetails = errorBuilder.toString();
                    }
                } catch (Exception e) {
                    log.warn("Error extracting validation errors via reflection", e);
                }
            }

            if (alertList != null) {
                try {
                    Class<?> alertListClass = alertList.getClass();
                    Method getAlertGrpMethod = alertListClass.getMethod("getValidationAlertGrp");
                    List<?> alertGrps = (List<?>) getAlertGrpMethod.invoke(alertList);
                    hasAlerts = (alertGrps != null && !alertGrps.isEmpty());
                } catch (Exception e) {
                    log.warn("Error checking validation alerts via reflection", e);
                }
            }

            // ========== Build and Return AckResponse ==========
            return AckResponse.builder()
                    // Existing fields
                    .ackId(receiptId)
                    .submissionId(submissionId)
                    .ackType(acceptanceStatus)
                    .timestamp(statusDate != null ? statusDate.toString() :
                            LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                    .ackFilePath(null) // SDK doesn't provide file path
                    .errorCodes(null) // TODO: Extract from validation errors if needed
                    .errorMessages(null) // TODO: Extract from validation errors if needed
                    .details(errorDetails != null ? errorDetails : "Acknowledgment retrieved successfully")

                    // Tax Identifiers
                    .efin(efin)
                    .ein(ein)
                    .tin(tin)

                    // Financial Amounts
                    .taxableIncomeAmt(taxableIncomeAmt)
                    .totalTaxAmt(totalTaxAmt)
                    .balanceDueAmt(balanceDueAmt)
                    .expectedRefundAmt(expectedRefundAmt)
                    .netIncomeLossAmt(netIncomeLossAmt)

                    // Form Details
                    .taxYear(taxYear)
                    .submissionType(submissionType)
                    .submissionCategoryCode(submissionCategoryCode)

                    // Timestamps
                    .electronicPostmarkTs(postmarkTs != null ? postmarkTs.toString() : null)
                    .irsReceivedDate(receivedDt != null ? receivedDt.toString() : null)
                    .taxPeriodEndDate(taxPeriodEndDt != null ? taxPeriodEndDt.toString() : null)

                    // IRS Identifiers
                    .irsSubmissionId(irsSubmissionId)
                    .receiptId(receiptId)

                    // Additional Fields
                    .paymentRequestCode(paymentRequestCode)
                    .hasValidationErrors(hasErrors)
                    .hasValidationAlerts(hasAlerts)

                    .build();

        } catch (Exception e) {
            log.error("Error building AckResponse via reflection", e);
            throw new MefException("ACK_PARSE_ERROR",
                    "Failed to parse acknowledgment response",
                    e.getMessage(), e);
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
