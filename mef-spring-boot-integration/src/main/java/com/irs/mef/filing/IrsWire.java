package com.irs.mef.filing;

import com.irs.mef.ack.AckSource;
import com.irs.mef.ack.StoredAck;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.xml.datatype.XMLGregorianCalendar;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * GetAck by submission id. Parsed straight into {@link StoredAck}. GetNewAcks is not called.
 */
@Component
@Slf4j
public final class IrsWire {

    private final Clock clock;

    public IrsWire(Clock clock) {
        this.clock = clock;
    }

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
}
