package com.irs.mef.inspector.api;

import com.irs.mef.ack.AckStore;
import com.irs.mef.ack.StoredAck;
import com.irs.mef.exception.MefException;
import com.irs.mef.inspector.api.InspectorSendViews.ApiError;
import com.irs.mef.inspector.api.InspectorSendViews.SendDetailView;
import com.irs.mef.inspector.api.InspectorSendViews.SendSummaryView;
import com.irs.mef.inspector.ring.SendSnapshot;
import com.irs.mef.inspector.ring.SendSnapshotRing;
import com.irs.mef.inspector.ring.SnapshotId;
import com.irs.mef.newsend.domain.NewSendSubmissionId;
import com.irs.mef.newsend.domain.NewSendSubmissionRecord;
import com.irs.mef.newsend.journal.NewSendSubmissionJournal;
import com.irs.mef.service.AcknowledgementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/inspector/sends")
@Slf4j
public class InspectorSendsController {

    private static final String NOT_LOGGED_IN = "NOT_LOGGED_IN";
    private static final String ACK_NOT_FOUND = "ACK_NOT_FOUND";
    private static final String ACK_RETRIEVAL_FAILED = "ACK_RETRIEVAL_FAILED";

    private final SendSnapshotRing ring;
    private final NewSendSubmissionJournal journal;
    private final AckStore ackStore;
    private final AcknowledgementService acknowledgementService;

    public InspectorSendsController(SendSnapshotRing ring,
                                    NewSendSubmissionJournal journal,
                                    AckStore ackStore,
                                    AcknowledgementService acknowledgementService) {
        this.ring = ring;
        this.journal = journal;
        this.ackStore = ackStore;
        this.acknowledgementService = acknowledgementService;
    }

    @GetMapping
    public List<SendSummaryView> list() {
        return ring.listNewestFirst().stream()
                .map(summary -> SendSummaryView.of(
                        summary,
                        journalRow(summary.submissionId()),
                        ackStore.find(summary.submissionId().value())))
                .toList();
    }

    @GetMapping("/{submissionId}")
    public ResponseEntity<?> detail(@PathVariable String submissionId) {
        Optional<SnapshotId> id = SnapshotId.tryParse(submissionId);
        if (id.isEmpty()) {
            return invalidSubmissionId();
        }
        Optional<SendSnapshot> snapshot = ring.get(id.get());
        if (snapshot.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, "SEND_NOT_FOUND",
                    "Submission " + id.get().value() + " is not in the local send ring");
        }
        return ResponseEntity.ok(SendDetailView.of(
                snapshot.get(), journalRow(id.get()), ackStore.find(id.get().value())));
    }

    @PostMapping("/{submissionId}/ack")
    public ResponseEntity<?> retrieveAck(@PathVariable String submissionId) {
        Optional<SnapshotId> id = SnapshotId.tryParse(submissionId);
        if (id.isEmpty()) {
            return invalidSubmissionId();
        }
        StoredAck ack;
        try {
            ack = acknowledgementService.retrieveAndRecordAcknowledgment(id.get().value());
        } catch (MefException e) {
            return ackFailure(e.getErrorCode(), e.getMessage(), e);
        } catch (RuntimeException e) {
            return ackFailure(ACK_RETRIEVAL_FAILED, e.getMessage(), e);
        }
        return ResponseEntity.ok(ack);
    }

    private Optional<NewSendSubmissionRecord> journalRow(SnapshotId id) {
        return journal.findBySubmissionId(new NewSendSubmissionId(id.value()));
    }

    private static ResponseEntity<ApiError> ackFailure(String code, String message, RuntimeException cause) {
        if (NOT_LOGGED_IN.equals(code)) {
            return error(HttpStatus.CONFLICT, NOT_LOGGED_IN, message);
        }
        if (ACK_NOT_FOUND.equals(code)) {
            return error(HttpStatus.NOT_FOUND, ACK_NOT_FOUND, message);
        }
        log.warn("inspector: GetAck failed: {}", cause.toString());
        return error(HttpStatus.BAD_GATEWAY, ACK_RETRIEVAL_FAILED,
                message != null ? message : "Acknowledgment retrieval failed");
    }

    private static ResponseEntity<ApiError> invalidSubmissionId() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_SUBMISSION_ID",
                "Submission id must match [0-9]{13}[a-z0-9]{7}");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(code, message));
    }
}
