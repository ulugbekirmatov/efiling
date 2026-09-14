package com.irs.mef.newsend.api;

import com.irs.mef.newsend.NewSendSubmissionService;
import com.irs.mef.newsend.NewSendSubmitResult;
import com.irs.mef.newsend.domain.NewSendOutcome;
import com.irs.mef.newsend.domain.NewSendSubmissionRecord;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Instant;

/**
 * Thin by construction: bind, map to a command, delegate, translate the outcome to a status.
 * No SDK type, no journal type and no business rule appears here.
 *
 * Routes are distinct from the legacy /mef/submissions/** so the two verticals never collide.
 */
@RestController
@RequestMapping("/mef/newsend/submissions")
@RequiredArgsConstructor
@Validated
@Slf4j
public class NewSendSubmissionController {

    private final NewSendSubmissionService service;

    /**
     * File one 94x return. 201 on first transmission, 200 on idempotent replay.
     * The Idempotency-Key header is REQUIRED: sending the same key twice returns the original
     * receipt and does not transmit again — the only safe way to retry a filing request.
     */
    @PostMapping
    public ResponseEntity<?> submit(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody NewSendSubmitRequest request) {
        NewSendSubmitResult result = service.submit(request.toCommand(idempotencyKey));
        NewSendOutcome outcome = result.outcome();

        if (outcome instanceof NewSendOutcome.Transmitted) {
            NewSendSubmissionRecord record = service
                    .findBySubmissionId(result.submissionId().value())
                    .orElseThrow(() -> new IllegalStateException(
                            "Transmitted submission missing from journal: " + result.submissionId().value()));
            NewSendSubmitResponse body = NewSendSubmitResponse.from(record, result);
            if (result.replay()) {
                return ResponseEntity.ok(body);
            }
            return ResponseEntity
                    .created(ServletUriComponentsBuilder.fromCurrentRequest()
                            .path("/{id}").buildAndExpand(result.submissionId().value()).toUri())
                    .body(body);
        }

        if (outcome instanceof NewSendOutcome.Rejected rejected) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(NewSendErrorResponse.builder()
                    .timestamp(Instant.now())
                    .status(HttpStatus.BAD_GATEWAY.value())
                    .errorCode(rejected.fault().code())
                    .message("The IRS refused the submission; nothing was filed. Correct the return "
                            + "and file under a NEW Idempotency-Key. " + rejected.fault().message())
                    .submissionId(result.submissionId().value())
                    .resubmitSafe(true)
                    .logHint(rejected.fault().logHint())
                    .build());
        }

        NewSendOutcome.Indeterminate indeterminate = (NewSendOutcome.Indeterminate) outcome;
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(NewSendErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.GATEWAY_TIMEOUT.value())
                .errorCode(indeterminate.fault().code())
                .message("Transmission outcome unknown — the filing MAY have reached the IRS. "
                        + "Do NOT resubmit. " + indeterminate.fault().message())
                .submissionId(result.submissionId().value())
                .resubmitSafe(false)
                .reconcileWith("GET /api/mef/submissions/" + result.submissionId().value()
                        + "/status (allow 2-5 minutes), then the acknowledgement endpoints")
                .logHint(indeterminate.fault().logHint())
                .build());
    }

    /** Local journal record by submission id. NOT live IRS status. */
    @GetMapping("/{submissionId}")
    public ResponseEntity<NewSendRecordResponse> bySubmissionId(@PathVariable String submissionId) {
        return service.findBySubmissionId(submissionId)
                .map(record -> ResponseEntity.ok(NewSendRecordResponse.from(record)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Reconcile by idempotency key — the post-crash "did my filing go out?" question. */
    @GetMapping("/by-request/{clientRequestId}")
    public ResponseEntity<NewSendRecordResponse> byClientRequestId(@PathVariable String clientRequestId) {
        return service.findByClientRequestId(clientRequestId)
                .map(record -> ResponseEntity.ok(NewSendRecordResponse.from(record)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
