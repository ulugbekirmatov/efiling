package com.irs.mef.newsend.gateway;

import com.irs.mef.newsend.domain.NewSendFault;
import com.irs.mef.newsend.domain.NewSendFiling;
import com.irs.mef.newsend.domain.NewSendManifestXml;
import com.irs.mef.newsend.domain.NewSendOutcome;
import com.irs.mef.newsend.domain.NewSendReceipt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.xml.datatype.XMLGregorianCalendar;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * The only class in the NewSend module that names a gov.irs.* type — and it names them as strings,
 * per the house pattern (Class.forName + Method.invoke to dodge Java 17 module-access failures in
 * the Metro/JAX-WS stack).
 *
 * Verified SDK v16 signatures used here (parsed from the mef_client_sdk.jar bytecode):
 * <pre>
 *   SubmissionXML(String, String) / SubmissionManifest(String, String)   [in-memory ctors ONLY]
 *   SubmissionBuilder.createIRSSubmissionArchive(String, SubmissionManifest, SubmissionXML, BinaryAttachment[])
 *   SubmissionBuilder.createPostmarkedSubmissionArchive(SubmissionArchive, GregorianCalendar)
 *   SubmissionBuilder.createSubmissionContainer(PostmarkedSubmissionArchive[])
 *   SendSubmissionsClient#invoke(ServiceContext, SubmissionContainer) -> SendSubmissionsResult
 *   SendSubmissionsResult#getDepositID() / #getSubmissionReceiptList()
 *   SubmissionReceiptList#getCnt():BigInteger / #getReceiptBySubmissionId(String)
 * </pre>
 *
 * The attachment element type is BinaryAttachment. NOT "SubmissionBinaryAttachment" — that class
 * does not exist in the v16 jar and is why the legacy service could never complete a send.
 * We pass a correctly-typed EMPTY array rather than null.
 */
@Component
@Slf4j
public class ReflectiveNewSendTransmitGateway implements NewSendTransmitGateway {

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

    @Override
    public NewSendOutcome transmit(Object serviceContext, NewSendFiling filing) {
        PreparedCall prepared = prepare(filing);
        Object result;
        try {
            result = prepared.invokeMethod().invoke(prepared.client(), serviceContext, prepared.container());
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return classify(cause, filing);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            // Method.invoke raises these BEFORE the target method runs — provably pre-wire.
            throw new NewSendCompositionException(NewSendFault.composition(e), e);
        }
        return readReceipt(result, filing, Instant.now());
    }

    /**
     * Everything local: manifest + return XML wrapping, archive, postmark, container, client and
     * method resolution. A failure here provably precedes any network I/O.
     */
    private PreparedCall prepare(NewSendFiling filing) {
        try {
            Class<?> submissionXmlClass = Class.forName(SUBMISSION_XML);
            Class<?> submissionManifestClass = Class.forName(SUBMISSION_MANIFEST);
            Class<?> submissionBuilderClass = Class.forName(SUBMISSION_BUILDER);
            Class<?> submissionArchiveClass = Class.forName(SUBMISSION_ARCHIVE);
            Class<?> postmarkedArchiveClass = Class.forName(POSTMARKED_ARCHIVE);
            Class<?> binaryAttachmentClass = Class.forName(BINARY_ATTACHMENT);

            // In-memory constructors ONLY: the file-based ones break SDK serialization.
            Object submissionXml = submissionXmlClass.getConstructor(String.class, String.class)
                    .newInstance(RETURN_XML_FILENAME, filing.command().returnXml());
            Object manifest = submissionManifestClass.getConstructor(String.class, String.class)
                    .newInstance(MANIFEST_FILENAME, NewSendManifestXml.render(filing));

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

            Class<?> clientClass = Class.forName(SEND_SUBMISSIONS_CLIENT);
            Object client = clientClass.getDeclaredConstructor().newInstance();
            Method invokeMethod = clientClass.getMethod("invoke",
                    Class.forName(SERVICE_CONTEXT), Class.forName(SUBMISSION_CONTAINER));

            return new PreparedCall(client, invokeMethod, container);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new NewSendCompositionException(NewSendFault.composition(cause), cause);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new NewSendCompositionException(NewSendFault.composition(e), e);
        }
    }

    /**
     * Classification, biased toward safety: only a recognised IRS business fault (ServiceException —
     * a SOAP fault response, meaning the IRS received and refused the request) becomes Rejected.
     * ANYTHING else — timeout, IOException, unrecognised ToolkitException — is Indeterminate,
     * because the container may already be at the IRS. Unknown never maps to "failed".
     */
    private NewSendOutcome classify(Throwable cause, NewSendFiling filing) {
        log.error("SendSubmissions threw for {}: {}", filing.submissionId().value(), cause.toString());
        if (isInstanceOfByName(cause, SERVICE_EXCEPTION)) {
            return new NewSendOutcome.Rejected(
                    NewSendFault.wire("NEWSEND_IRS_REJECTED", cause, filing.submissionId()));
        }
        return new NewSendOutcome.Indeterminate(
                NewSendFault.wire("NEWSEND_TRANSPORT_UNKNOWN", cause, filing.submissionId()));
    }

    /**
     * A deposit id alone is NOT success. Success requires a receipt bearing OUR submission id:
     * receipt found -> Transmitted; deposit id without our receipt, or no deposit id -> Indeterminate.
     * A missing receipt TIMESTAMP is non-fatal — it falls back to our own clock, flagged LOCAL.
     */
    private NewSendOutcome readReceipt(Object sendSubmissionsResult, NewSendFiling filing, Instant observedAt) {
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
            // The wire call already succeeded; failing to READ the result must not look like a reject.
            return new NewSendOutcome.Indeterminate(
                    NewSendFault.wire("NEWSEND_RESULT_UNREADABLE", e, filing.submissionId()));
        }
        if (depositId == null || depositId.isBlank() || receipt == null) {
            return new NewSendOutcome.Indeterminate(NewSendFault.noReceipt(depositId, filing.submissionId()));
        }
        Instant receiptTimestamp = probeReceiptTimestamp(receipt);
        NewSendReceipt.TimestampSource source = receiptTimestamp != null
                ? NewSendReceipt.TimestampSource.IRS
                : NewSendReceipt.TimestampSource.LOCAL;
        return new NewSendOutcome.Transmitted(new NewSendReceipt(
                depositId,
                filing.submissionId(),
                receiptTimestamp != null ? receiptTimestamp : observedAt,
                source,
                receiptCount));
    }

    /** SubmissionReceiptGrp getter names are not pinned by the jar parse; probe defensively. */
    private Instant probeReceiptTimestamp(Object receipt) {
        for (String getterName : RECEIPT_TIMESTAMP_GETTERS) {
            try {
                Object value = receipt.getClass().getMethod(getterName).invoke(receipt);
                Instant converted = toInstant(value);
                if (converted != null) {
                    return converted;
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                // try the next getter name
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
}
