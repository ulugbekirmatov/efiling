package com.irs.mef.inspector.api;

import com.irs.mef.ack.StoredAck;
import com.irs.mef.inspector.ring.CapturedXml;
import com.irs.mef.inspector.ring.OmittedAttachment;
import com.irs.mef.inspector.ring.SendSnapshot;
import com.irs.mef.inspector.ring.SnapshotSummary;
import com.irs.mef.inspector.ring.SoapCapture;
import com.irs.mef.newsend.domain.NewSendSubmissionRecord;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class InspectorSendViews {

    private InspectorSendViews() {
    }

    public record SendSummaryView(
            String submissionId,
            Instant capturedAt,
            String environment,
            String einMasked,
            String formCode,
            String periodLabel,
            String state,
            String depositId,
            String ackType) {

        public static SendSummaryView of(SnapshotSummary summary,
                                  Optional<NewSendSubmissionRecord> journalRow,
                                  Optional<StoredAck> ack) {
            return new SendSummaryView(
                    summary.submissionId().value(),
                    summary.capturedAt(),
                    summary.environment(),
                    summary.einMasked(),
                    summary.formCode(),
                    summary.periodLabel(),
                    journalRow.map(row -> row.state().name()).orElse(null),
                    journalRow.map(InspectorSendViews::depositIdOf).orElse(null),
                    ack.map(StoredAck::ackType).orElse(null));
        }
    }

    public record SendDetailView(
            String submissionId,
            Instant capturedAt,
            String environment,
            String einMasked,
            String formType,
            String taxPeriodBegin,
            String taxPeriodEnd,
            String clientRequestId,
            String returnXmlSha256,
            TransmissionView transmission,
            DocumentsView documents,
            MimeView mime,
            StoredAck ack) {

        public static SendDetailView of(SendSnapshot snapshot,
                                 Optional<NewSendSubmissionRecord> journalRow,
                                 Optional<StoredAck> ack) {
            return new SendDetailView(
                    snapshot.submissionId().value(),
                    snapshot.capturedAt(),
                    snapshot.environment(),
                    snapshot.einMasked(),
                    snapshot.formType().code(),
                    snapshot.taxPeriod().begin().toString(),
                    snapshot.taxPeriod().end().toString(),
                    snapshot.clientRequestId(),
                    snapshot.returnXmlSha256(),
                    journalRow.map(TransmissionView::of).orElse(null),
                    DocumentsView.of(snapshot),
                    new MimeView(snapshot.mimeRequest().contentType(), snapshot.mimeRequest().attachments()),
                    ack.orElse(null));
        }
    }

    public record TransmissionView(
            String state,
            String depositId,
            Instant createdAt,
            Instant completedAt,
            Instant receiptTimestamp,
            String faultCode,
            String faultMessage) {

        public static TransmissionView of(NewSendSubmissionRecord row) {
            return new TransmissionView(
                    row.state().name(),
                    depositIdOf(row),
                    row.createdAt(),
                    row.completedAt(),
                    row.receipt() == null ? null : row.receipt().receiptTimestamp(),
                    row.fault() == null ? null : row.fault().code(),
                    row.fault() == null ? null : row.fault().message());
        }
    }

    public record DocumentsView(
            DocumentView returnXml,
            DocumentView manifestXml,
            DocumentView soapRequest,
            DocumentView soapResponse) {

        public static DocumentsView of(SendSnapshot snapshot) {
            return new DocumentsView(
                    DocumentView.of(snapshot.returnXml()),
                    DocumentView.of(snapshot.manifestXml()),
                    DocumentView.of(snapshot.mimeRequest().soapCapture()),
                    DocumentView.of(snapshot.soapResponse()));
        }
    }

    public record DocumentView(String text, String missingReason) {

        public static DocumentView of(CapturedXml xml) {
            return new DocumentView(xml.pretty(), null);
        }

        public static DocumentView of(SoapCapture capture) {
            if (capture instanceof SoapCapture.Present present) {
                return of(present.xml());
            }
            return new DocumentView(null, ((SoapCapture.Missing) capture).reason());
        }
    }

    public record MimeView(String contentType, List<OmittedAttachment> attachments) {
    }

    public record ApiError(String code, String message) {
    }

    private static String depositIdOf(NewSendSubmissionRecord row) {
        return row.receipt() == null ? null : row.receipt().depositId();
    }
}
