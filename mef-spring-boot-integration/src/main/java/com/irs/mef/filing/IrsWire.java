package com.irs.mef.filing;

import com.irs.mef.ack.AckSource;
import com.irs.mef.ack.StoredAck;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.xml.datatype.XMLGregorianCalendar;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.GregorianCalendar;
import java.util.Date;
import java.util.Calendar;
import java.time.ZoneOffset;
import java.time.Instant;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Component
@Slf4j
final class IrsWire implements TransmitGateway {

    private final Clock clock;
    private final SendSubmissionsWireTap wireTap;

    private static final String SUBMISSION_XML = "gov.irs.mef.inputcomposition.SubmissionXML";
    private static final String SUBMISSION_MANIFEST = "gov.irs.mef.inputcomposition.SubmissionManifest";
    private static final String SUBMISSION_BUILDER = "gov.irs.mef.inputcomposition.SubmissionBuilder";
    private static final String SUBMISSION_ARCHIVE = "gov.irs.mef.inputcomposition.SubmissionArchive";
    private static final String POSTMARKED_ARCHIVE = "gov.irs.mef.inputcomposition.PostmarkedSubmissionArchive";
    private static final String SUBMISSION_CONTAINER = "gov.irs.mef.inputcomposition.SubmissionContainer";
    private static final String BINARY_ATTACHMENT = "gov.irs.mef.inputcomposition.BinaryAttachment";
    private static final String SERVICE_CONTEXT = "gov.irs.mef.services.ServiceContext";
    private static final String SEND_SUBMISSIONS_CLIENT = "gov.irs.mef.services.transmitter.SendSubmissionsClient";
    private static final String SERVICE_EXCEPTION = "gov.irs.mef.exception.ServiceException";

    private static final String RETURN_XML_FILENAME = "Return.xml";
    private static final String MANIFEST_FILENAME = "manifest.xml";
    private static final String[] RECEIPT_TIMESTAMP_GETTERS =
            {"getTimestamp", "getSubmissionReceiptTs", "getReceiptTs", "getReceiptTimestamp", "getTs"};

    IrsWire(Clock clock, SendSubmissionsWireTap wireTap) {
        this.clock = clock;
        this.wireTap = wireTap;
    }

    @Override
    public Optional<StoredAck> getAck(Object serviceContext, String submissionId) {
        try {
            Class<?> serviceContextClass = Class.forName("gov.irs.mef.services.ServiceContext");
            Class<?> clientClass = Class.forName("gov.irs.mef.services.transmitter.GetAckClient");
            Object client = clientClass.getDeclaredConstructor().newInstance();
            Method invoke = clientClass.getMethod("invoke", serviceContextClass, String.class);
            Object result = invoke.invoke(client, serviceContext, submissionId);
            if (result == null) {
                throw new IllegalStateException("GetAck returned no result for " + submissionId);
            }
            Object ackList = Class.forName("gov.irs.mef.services.transmitter.GetAckResult")
                    .getMethod("getAcknowledgementList")
                    .invoke(result);
            if (ackList == null) {
                return Optional.empty();
            }
            List<?> acks = (List<?>) Class.forName("gov.irs.mef.AcknowledgementList")
                    .getMethod("getAcknowledgements")
                    .invoke(ackList);
            if (acks == null || acks.isEmpty()) {
                return Optional.empty();
            }
            if (acks.size() > 1) {
                log.warn("Multiple acknowledgments returned for submission ID: {} (count: {})",
                        submissionId, acks.size());
            }
            return Optional.of(parse(acks.get(0), submissionId));
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(
                    cause.getMessage() != null ? cause.getMessage() : cause.toString(), cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    e.getMessage() != null ? e.getMessage() : e.toString(), e);
        }
    }

    StoredAck parse(Object ack, String requestedSubmissionId) {
        try {
            Class<?> ackClass = ack.getClass();
            Function<String, Object> field = methodName -> {
                try {
                    return ackClass.getMethod(methodName).invoke(ack);
                } catch (Exception e) {
                    return null;
                }
            };

            String submissionId = (String) field.apply("getSubmissionId");
            if (submissionId == null || submissionId.isBlank()) {
                submissionId = requestedSubmissionId;
            }
            String acceptanceStatus = (String) field.apply("getAcceptanceStatusTxt");
            String efin = (String) field.apply("getEFIN");
            String ein = (String) field.apply("getEIN");
            String tin = (String) field.apply("getTIN");

            XMLGregorianCalendar statusDate = (XMLGregorianCalendar) field.apply("getStatusDt");
            XMLGregorianCalendar postmarkTs = (XMLGregorianCalendar) field.apply("getElectronicPostmarkTs");
            XMLGregorianCalendar receivedDt = (XMLGregorianCalendar) field.apply("getIRSReceivedDt");
            XMLGregorianCalendar taxPeriodEndDt = (XMLGregorianCalendar) field.apply("getTaxPeriodEndDt");

            Long taxableIncomeAmt = longOrNull((BigInteger) field.apply("getTaxableIncomeAmt"));
            Long totalTaxAmt = longOrNull((BigInteger) field.apply("getTotalTaxAmt"));
            Long balanceDueAmt = longOrNull((BigInteger) field.apply("getBalanceDueAmt"));
            Long expectedRefundAmt = longOrNull((BigInteger) field.apply("getExpectedRefundAmt"));
            Long netIncomeLossAmt = longOrNull((BigInteger) field.apply("getNetIncomeLossAmt"));

            String taxYear = (String) field.apply("getTaxYr");
            String submissionType = (String) field.apply("getSubmissionTyp");
            String submissionCategoryCode = (String) field.apply("getExtndSubmissionCategoryCd");
            String irsSubmissionId = (String) field.apply("getIRSSubmissionId");
            String receiptId = (String) field.apply("getReceiptId");
            String paymentRequestCode = (String) field.apply("getPaymentRequestRcvdCd");

            List<String> errorCodes = new ArrayList<>();
            List<String> errorMessages = new ArrayList<>();
            String details = validationDetails(field.apply("getValidationErrorList"), submissionId,
                    errorCodes, errorMessages);
            boolean hasErrors = !errorCodes.isEmpty();
            boolean hasAlerts = hasAlerts(field.apply("getValidationAlertList"));
            if (details == null) {
                details = "Acknowledgment retrieved successfully";
            }

            return new StoredAck(
                    receiptId,
                    submissionId,
                    clock.instant(),
                    AckSource.GET_ACK,
                    acceptanceStatus,
                    statusDate != null ? statusDate.toString() : clock.instant().toString(),
                    null,
                    errorCodes,
                    errorMessages,
                    details,
                    efin,
                    ein,
                    tin,
                    taxableIncomeAmt,
                    totalTaxAmt,
                    balanceDueAmt,
                    expectedRefundAmt,
                    netIncomeLossAmt,
                    taxYear,
                    submissionType,
                    submissionCategoryCode,
                    postmarkTs != null ? postmarkTs.toString() : null,
                    receivedDt != null ? receivedDt.toString() : null,
                    taxPeriodEndDt != null ? taxPeriodEndDt.toString() : null,
                    irsSubmissionId,
                    receiptId,
                    paymentRequestCode,
                    hasErrors,
                    hasAlerts);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse acknowledgment response", e);
        }
    }

    private String validationDetails(Object errorList, String submissionId,
                                     List<String> errorCodes, List<String> errorMessages) {
        if (errorList == null) {
            return null;
        }
        try {
            List<?> groups = (List<?>) errorList.getClass().getMethod("getValidationErrorGrp").invoke(errorList);
            if (groups == null || groups.isEmpty()) {
                return null;
            }
            Object errorCount = errorList.getClass().getMethod("getErrorCnt").invoke(errorList);
            StringBuilder details = new StringBuilder();
            details.append("Validation errors (").append(errorCount).append("):\n");
            for (Object error : groups) {
                Class<?> groupClass = error.getClass();
                String ruleNum = (String) groupClass.getMethod("getRuleNum").invoke(error);
                String severity = (String) groupClass.getMethod("getSeverityCd").invoke(error);
                String message = (String) groupClass.getMethod("getErrorMessageTxt").invoke(error);
                String xpath = (String) groupClass.getMethod("getXpathContentTxt").invoke(error);
                String fieldValue = (String) groupClass.getMethod("getFieldValueTxt").invoke(error);
                log.error("VALIDATION ERROR for SubmissionID={}: Rule={}, Severity={}, Message={}",
                        submissionId, ruleNum, severity, message);
                details.append("  - Rule ").append(ruleNum)
                        .append(" [").append(severity).append("]: ")
                        .append(message);
                if (xpath != null) {
                    details.append(" (XPath: ").append(xpath).append(")");
                }
                if (fieldValue != null) {
                    details.append(" [Value: ").append(fieldValue).append("]");
                }
                details.append('\n');
                errorCodes.add(ruleNum);
                errorMessages.add("[" + severity + "] " + message
                        + (fieldValue != null && !fieldValue.isBlank() ? " [Value: " + fieldValue + "]" : ""));
            }
            return details.toString();
        } catch (Exception e) {
            log.warn("Error extracting validation errors via reflection", e);
            return null;
        }
    }

    private static boolean hasAlerts(Object alertList) {
        if (alertList == null) {
            return false;
        }
        try {
            List<?> groups = (List<?>) alertList.getClass().getMethod("getValidationAlertGrp").invoke(alertList);
            return groups != null && !groups.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static Long longOrNull(BigInteger value) {
        return value != null ? value.longValue() : null;
    }

    @Override
    public FilingOutcome transmit(Object serviceContext, Preflight.Cleared cleared) {
        Filing filing = cleared.prepared();
        PreparedCall prepared = prepare(filing, cleared.manifestXml());
        try (SendSubmissionsWireTap.Session tap = wireTap.open(filing)) {
            tap.attach(prepared.client());
            try {
                Object result = prepared.invokeMethod().invoke(prepared.client(), serviceContext, prepared.container());
                return readReceipt(result, filing, Instant.now());
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                return classify(cause, filing);
            } catch (IllegalAccessException | IllegalArgumentException e) {
                // Method.invoke raises these BEFORE the target method runs — provably pre-wire.
                throw new CompositionException(IrsFault.composition(e), e);
            }
        }
    }

    byte[] buildArchive(Preflight preflight) {
        Assembled assembled = assemble(preflight.prepared(), preflight.manifestText());
        try {
            Object bytes = assembled.archive().getClass().getMethod("getBytes").invoke(assembled.archive());
            if (!(bytes instanceof byte[] zip) || zip.length == 0) {
                IllegalStateException empty = new IllegalStateException("submission archive was empty");
                throw new CompositionException(IrsFault.composition(empty), empty);
            }
            return zip;
        } catch (CompositionException e) {
            throw e;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new CompositionException(IrsFault.composition(cause), cause);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new CompositionException(IrsFault.composition(e), e);
        }
    }

    private PreparedCall prepare(Filing filing, String manifestXml) {
        Assembled assembled = assemble(filing, manifestXml);
        try {
            Class<?> clientClass = Class.forName(SEND_SUBMISSIONS_CLIENT);
            Object client = clientClass.getDeclaredConstructor().newInstance();
            Method invokeMethod = clientClass.getMethod("invoke",
                    Class.forName(SERVICE_CONTEXT), Class.forName(SUBMISSION_CONTAINER));
            return new PreparedCall(client, invokeMethod, assembled.container());
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new CompositionException(IrsFault.composition(e), e);
        }
    }

    /** In-memory constructors ONLY: the file-based ones break SDK serialization. */
    private Assembled assemble(Filing filing, String manifestXml) {
        ensureToolkitHome();
        try {
            Class<?> submissionXmlClass = Class.forName(SUBMISSION_XML);
            Class<?> submissionManifestClass = Class.forName(SUBMISSION_MANIFEST);
            Class<?> submissionBuilderClass = Class.forName(SUBMISSION_BUILDER);
            Class<?> submissionArchiveClass = Class.forName(SUBMISSION_ARCHIVE);
            Class<?> postmarkedArchiveClass = Class.forName(POSTMARKED_ARCHIVE);
            Class<?> binaryAttachmentClass = Class.forName(BINARY_ATTACHMENT);

            Object submissionXml = submissionXmlClass.getConstructor(String.class, String.class)
                    .newInstance(RETURN_XML_FILENAME, filing.command().returnXml());
            Object manifest = submissionManifestClass.getConstructor(String.class, String.class)
                    .newInstance(MANIFEST_FILENAME, manifestXml);

            Object emptyAttachments = Array.newInstance(binaryAttachmentClass, 0);
            Method createArchive = submissionBuilderClass.getMethod("createIRSSubmissionArchive",
                    String.class, submissionManifestClass, submissionXmlClass, emptyAttachments.getClass());
            Object archive = createArchive.invoke(null,
                    filing.submissionId().value(), manifest, submissionXml, emptyAttachments);

            Method createPostmarked = submissionBuilderClass.getMethod("createPostmarkedSubmissionArchive",
                    submissionArchiveClass, GregorianCalendar.class);
            Object postmarked = createPostmarked.invoke(null, archive,
                    GregorianCalendar.from(filing.postmark().atZone(ZoneOffset.UTC)));

            Object postmarkedArray = Array.newInstance(postmarkedArchiveClass, 1);
            Array.set(postmarkedArray, 0, postmarked);
            Method createContainer = submissionBuilderClass.getMethod("createSubmissionContainer",
                    postmarkedArray.getClass());
            Object container = createContainer.invoke(null, postmarkedArray);
            return new Assembled(archive, container);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new CompositionException(IrsFault.composition(cause), cause);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new CompositionException(IrsFault.composition(e), e);
        }
    }

    private static void ensureToolkitHome() {
        String existing = System.getProperty("A2A_TOOLKIT_HOME");
        if (existing != null && !existing.isBlank()) {
            return;
        }
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path home = cwd.resolve("src/main/resources/mef_config");
        if (!Files.isDirectory(home)) {
            home = cwd.resolve("mef-spring-boot-integration/src/main/resources/mef_config");
        }
        if (Files.isDirectory(home)) {
            System.setProperty("A2A_TOOLKIT_HOME", home.toAbsolutePath().toString());
        }
    }

    private FilingOutcome classify(Throwable cause, Filing filing) {
        log.error("SendSubmissions threw for {}: {}", filing.submissionId().value(), cause.toString());
        if (isInstanceOfByName(cause, SERVICE_EXCEPTION)) {
            return new FilingOutcome.Rejected(
                    IrsFault.wire("NEWSEND_IRS_REJECTED", cause, filing.submissionId()));
        }
        return new FilingOutcome.Indeterminate(
                IrsFault.wire("NEWSEND_TRANSPORT_UNKNOWN", cause, filing.submissionId()));
    }

    private FilingOutcome readReceipt(Object sendSubmissionsResult, Filing filing, Instant observedAt) {
        String depositId;
        Object receipt = null;
        int receiptCount = 0;
        try {
            depositId = (String) sendSubmissionsResult.getClass()
                    .getMethod("getDepositID").invoke(sendSubmissionsResult);
            Object receiptList = sendSubmissionsResult.getClass()
                    .getMethod("getSubmissionReceiptList").invoke(sendSubmissionsResult);
            if (receiptList != null) {
                Object count = receiptList.getClass().getMethod("getCnt").invoke(receiptList);
                receiptCount = count instanceof Number number ? number.intValue() : 0;
                receipt = receiptList.getClass().getMethod("getReceiptBySubmissionId", String.class)
                        .invoke(receiptList, filing.submissionId().value());
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return new FilingOutcome.Indeterminate(
                    IrsFault.wire("NEWSEND_RESULT_UNREADABLE", e, filing.submissionId()));
        }
        if (depositId == null || depositId.isBlank() || receipt == null) {
            return new FilingOutcome.Indeterminate(IrsFault.noReceipt(depositId, filing.submissionId()));
        }
        Instant receiptTimestamp = probeReceiptTimestamp(receipt);
        TransmissionReceipt.TimestampSource source = receiptTimestamp != null
                ? TransmissionReceipt.TimestampSource.IRS
                : TransmissionReceipt.TimestampSource.LOCAL;
        return new FilingOutcome.Transmitted(new TransmissionReceipt(
                depositId,
                filing.submissionId(),
                receiptTimestamp != null ? receiptTimestamp : observedAt,
                source,
                receiptCount));
    }

    private Instant probeReceiptTimestamp(Object receipt) {
        for (String getterName : RECEIPT_TIMESTAMP_GETTERS) {
            try {
                Object value = receipt.getClass().getMethod(getterName).invoke(receipt);
                Instant converted = toInstant(value);
                if (converted != null) {
                    return converted;
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
            }
        }
        return null;
    }

    private Instant toInstant(Object value) {
        if (value instanceof XMLGregorianCalendar xmlCalendar) {
            return xmlCalendar.toGregorianCalendar().toInstant();
        }
        if (value instanceof Calendar calendar) {
            return calendar.toInstant();
        }
        if (value instanceof Date date) {
            return date.toInstant();
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        return null;
    }

    private boolean isInstanceOfByName(Throwable throwable, String className) {
        for (Class<?> type = throwable.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals(className)) {
                return true;
            }
        }
        return false;
    }

    private record PreparedCall(Object client, Method invokeMethod, Object container) {
    }

    private record Assembled(Object archive, Object container) {
    }
}
