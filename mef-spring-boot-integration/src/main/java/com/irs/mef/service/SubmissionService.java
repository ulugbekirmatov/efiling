package com.irs.mef.service;

import com.irs.mef.config.MefSdkConfig;
import com.irs.mef.dto.SubmitRequest;
import com.irs.mef.dto.SubmitResponse;
import com.irs.mef.exception.MefException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.GregorianCalendar;

/**
 * Service for handling submission operations with IRS MeF.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SubmissionService {

    private final MefClientService mefClientService;
    private final MefSdkConfig mefConfig;

    /**
     * Submit tax returns to IRS MeF.
     * Uses reflection to load MeF SDK classes to avoid Java module access issues.
     *
     * @param request Submission request with file path and metadata
     * @return Submission response with status and message ID
     */
    public SubmitResponse submitSubmission(SubmitRequest request) {
        log.info("Submitting submission ID: {}", request.getSubmissionId());

        // Verify logged in
        if (!mefClientService.isLoggedIn()) {
            throw new MefException("NOT_LOGGED_IN", "Not logged in to IRS MeF",
                    "Please login before submitting");
        }

        // Validate submission file exists
        File submissionFile = new File(request.getSubmissionFilePath());
        if (!submissionFile.exists()) {
            throw new MefException("FILE_NOT_FOUND", "Submission file not found",
                    "File: " + request.getSubmissionFilePath());
        }

        try {
            // Get ServiceContext from login session
            Object serviceContext = mefClientService.getCurrentServiceContext();

            log.info("Creating SendSubmissionsClient...");

            // Load SDK classes via reflection
            Class<?> serviceContextClass = Class.forName("gov.irs.mef.services.ServiceContext");
            Class<?> sendSubmissionsClientClass = Class.forName("gov.irs.mef.services.transmitter.SendSubmissionsClient");
            Class<?> submissionXMLClass = Class.forName("gov.irs.mef.inputcomposition.SubmissionXML");
            Class<?> submissionManifestClass = Class.forName("gov.irs.mef.inputcomposition.SubmissionManifest");
            Class<?> submissionBuilderClass = Class.forName("gov.irs.mef.inputcomposition.SubmissionBuilder");
            Class<?> submissionArchiveClass = Class.forName("gov.irs.mef.inputcomposition.SubmissionArchive");
            Class<?> postmarkedSubmissionArchiveClass = Class.forName("gov.irs.mef.inputcomposition.PostmarkedSubmissionArchive");
            Class<?> submissionContainerClass = Class.forName("gov.irs.mef.inputcomposition.SubmissionContainer");

            // Create SendSubmissionsClient via reflection
            Object client = sendSubmissionsClientClass.getDeclaredConstructor().newInstance();

            // Load XML file content into memory (SDK requires in-memory objects)
            String xmlContent = Files.readString(submissionFile.toPath());

            // Create SubmissionXML via reflection: new SubmissionXML("Return.xml", xmlContent)
            Object submissionXML = submissionXMLClass
                .getConstructor(String.class, String.class)
                .newInstance("Return.xml", xmlContent);

            // Create submission manifest (REQUIRED by SDK, also in-memory)
            Object manifest = createIRSManifestReflection(
                submissionManifestClass,
                request.getSubmissionId(),
                request.getEfin(),
                request.getTin(),
                request.getTaxPeriodBegin(),
                request.getTaxPeriodEnd()
            );

            // Create SubmissionArchive using SubmissionBuilder via reflection
            Method createIRSSubmissionArchiveMethod = submissionBuilderClass.getMethod(
                "createIRSSubmissionArchive",
                String.class,
                submissionManifestClass,
                submissionXMLClass,
                Array.newInstance(Class.forName("gov.irs.mef.inputcomposition.SubmissionBinaryAttachment"), 0).getClass()
            );
            Object archive = createIRSSubmissionArchiveMethod.invoke(
                null,
                request.getSubmissionId(),
                manifest,
                submissionXML,
                null
            );

            // Create PostmarkedSubmissionArchive with current timestamp via reflection
            Method createPostmarkedSubmissionArchiveMethod = submissionBuilderClass.getMethod(
                "createPostmarkedSubmissionArchive",
                submissionArchiveClass,
                GregorianCalendar.class
            );
            Object postmarkedArchive = createPostmarkedSubmissionArchiveMethod.invoke(
                null,
                archive,
                new GregorianCalendar()
            );

            // Create PostmarkedSubmissionArchive array via reflection
            Object postmarkedArchiveArray = Array.newInstance(postmarkedSubmissionArchiveClass, 1);
            Array.set(postmarkedArchiveArray, 0, postmarkedArchive);

            // Create SubmissionContainer via reflection
            Method createSubmissionContainerMethod = submissionBuilderClass.getMethod(
                "createSubmissionContainer",
                postmarkedArchiveArray.getClass()
            );
            Object container = createSubmissionContainerMethod.invoke(null, postmarkedArchiveArray);

            log.info("Invoking IRS MeF SendSubmissions service for submission ID: {}",
                     request.getSubmissionId());

            // Invoke SendSubmissions via reflection
            Method invokeMethod = sendSubmissionsClientClass.getMethod("invoke",
                serviceContextClass, submissionContainerClass);
            Object result = invokeMethod.invoke(client, serviceContext, container);

            // Extract result data via reflection
            Class<?> sendSubmissionsResultClass = Class.forName("gov.irs.mef.services.transmitter.SendSubmissionsResult");
            Method getDepositIDMethod = sendSubmissionsResultClass.getMethod("getDepositID");
            Method getSubmissionReceiptListMethod = sendSubmissionsResultClass.getMethod("getSubmissionReceiptList");

            String depositId = (String) getDepositIDMethod.invoke(result);
            Object receiptList = getSubmissionReceiptListMethod.invoke(result);

            // Extract submission receipt details
            String status = "Accepted";
            String messageId = depositId;

            if (receiptList != null) {
                Class<?> receiptListClass = Class.forName("gov.irs.mef.SubmissionReceiptList");
                Method getCntMethod = receiptListClass.getMethod("getCnt");
                Object cntObj = getCntMethod.invoke(receiptList);
                if (cntObj != null) {
                    int cnt = ((Number) cntObj).intValue();
                    if (cnt > 0) {
                        Method getReceiptBySubmissionIdMethod = receiptListClass.getMethod(
                            "getReceiptBySubmissionId", String.class);
                        Object receipt = getReceiptBySubmissionIdMethod.invoke(receiptList, request.getSubmissionId());
                        if (receipt != null) {
                            log.info("Receipt found for submission ID: {}", request.getSubmissionId());
                        }
                    }
                }
            }

            log.info("Submission successful. Submission ID: {}, Deposit ID: {}",
                    request.getSubmissionId(), depositId);

            return SubmitResponse.builder()
                    .submissionId(request.getSubmissionId())
                    .depositId(depositId)
                    .messageId(messageId)
                    .status(status)
                    .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                    .message("Submission sent successfully to IRS MeF ATS")
                    .accepted(true)
                    .build();

        } catch (Exception e) {
            log.error("Submission failed for ID: {}", request.getSubmissionId(), e);
            throw new MefException("SUBMISSION_FAILED", "Failed to submit to IRS MeF",
                    e.getMessage(), e);
        }
    }

    /**
     * Create a submission archive from individual files.
     * Helper method to package submission files into ZIP archive format.
     *
     * @param submissionId Submission identifier
     * @param returnXmlPath Path to return XML file
     * @param attachments Optional attachment file paths
     * @return Path to created archive file
     */
    public String createSubmissionArchive(String submissionId, String returnXmlPath,
                                         String... attachments) {
        log.info("Creating submission archive for ID: {}", submissionId);

        try {
            // TODO: Implement actual MeF SDK archive creation
            // Example code structure:
            //
            // SubmissionArchive archive = new SubmissionArchive();
            // archive.setSubmissionId(submissionId);
            //
            // // Add return XML
            // archive.addReturnData(new File(returnXmlPath));
            //
            // // Add attachments if any
            // if (attachments != null) {
            //     for (String attachment : attachments) {
            //         archive.addAttachment(new File(attachment));
            //     }
            // }
            //
            // // Create archive file
            // String archivePath = "submissions/" + submissionId + ".zip";
            // archive.create(new File(archivePath));
            //
            // return archivePath;

            // PLACEHOLDER - Replace with actual SDK call
            String archivePath = "submissions/" + submissionId + ".zip";
            log.info("Submission archive created: {}", archivePath);
            return archivePath;

        } catch (Exception e) {
            log.error("Failed to create submission archive for ID: {}", submissionId, e);
            throw new MefException("ARCHIVE_CREATION_FAILED",
                    "Failed to create submission archive", e.getMessage(), e);
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

    /**
     * Create IRS submission manifest XML using reflection.
     * The manifest is required metadata about the submission.
     *
     * @param submissionManifestClass The SubmissionManifest class loaded via reflection
     * @param submissionId Submission identifier
     * @param efin Electronic Filer Identification Number
     * @param tin Taxpayer Identification Number (EIN)
     * @param taxPeriodBegin Tax period begin date
     * @param taxPeriodEnd Tax period end date
     * @return SubmissionManifest object with XML metadata
     */
    private Object createIRSManifestReflection(
            Class<?> submissionManifestClass,
            String submissionId,
            String efin,
            String tin,
            java.time.LocalDate taxPeriodBegin,
            java.time.LocalDate taxPeriodEnd) {

        try {
            String taxYr = "";
            if (taxPeriodEnd != null) {
                taxYr = "  <TaxYr>" + taxPeriodEnd.getYear() + "</TaxYr>\n";
            } else if (taxPeriodBegin != null) {
                taxYr = "  <TaxYr>" + taxPeriodBegin.getYear() + "</TaxYr>\n";
            }
            String manifestXml = String.format(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<IRSSubmissionManifest xmlns=\"http://www.irs.gov/efile\" xmlns:efile=\"http://www.irs.gov/efile\">\n" +
                "  <SubmissionId>%s</SubmissionId>\n" +
                "  <EFIN>%s</EFIN>\n" +
                "%s" +
                "  <GovernmentCd>IRS</GovernmentCd>\n" +
                "  <FederalSubmissionTypeCd>941</FederalSubmissionTypeCd>\n" +
                "%s%s" +
                "  <TIN>%s</TIN>\n" +
                "</IRSSubmissionManifest>",
                submissionId,
                efin != null ? efin : "",
                taxYr,
                taxPeriodBegin != null ? "  <TaxPeriodBeginDt>" + taxPeriodBegin.toString() + "</TaxPeriodBeginDt>\n" : "",
                taxPeriodEnd != null ? "  <TaxPeriodEndDt>" + taxPeriodEnd.toString() + "</TaxPeriodEndDt>\n" : "",
                tin != null ? tin : ""
            );

            log.debug("Generated manifest XML:\n{}", manifestXml);

            // Create in-memory SubmissionManifest via reflection (SDK requires in-memory objects for serialization)
            return submissionManifestClass
                .getConstructor(String.class, String.class)
                .newInstance("manifest.xml", manifestXml);

        } catch (Exception e) {
            log.error("Failed to create submission manifest", e);
            throw new MefException("MANIFEST_CREATION_FAILED",
                "Failed to create submission manifest", e.getMessage(), e);
        }
    }
}
