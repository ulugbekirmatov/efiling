package com.irs.mef.web;

import com.irs.mef.filing.FilingDesk;
import com.irs.mef.filing.SubmitResult;
import com.irs.mef.filing.FilingOutcome;
import com.irs.mef.filing.SubmissionRecord;
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
public class FilingController {

    private final FilingDesk service;

    /**
     * File one 94x return. 201 on first transmission, 200 on idempotent replay.
     * The Idempotency-Key header is REQUIRED: sending the same key twice returns the original
     * receipt and does not transmit again — the only safe way to retry a filing request.
     */
    @PostMapping
    public ResponseEntity<?> submit(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody FilingRequest request) {
        SubmitResult result = service.submit(request.toCommand(idempotencyKey));
        FilingOutcome outcome = result.outcome();

        if (outcome instanceof FilingOutcome.Transmitted) {
            SubmissionRecord record = service
                    .findBySubmissionId(result.submissionId().value())
                    .orElseThrow(() -> new IllegalStateException(
                            "Transmitted submission missing from journal: " + result.submissionId().value()));
            FilingResponse body = FilingResponse.from(record, result);
            if (result.replay()) {
                return ResponseEntity.ok(body);
            }
            return ResponseEntity
                    .created(ServletUriComponentsBuilder.fromCurrentRequest()
                            .path("/{id}").buildAndExpand(result.submissionId().value()).toUri())
                    .body(body);
        }

        if (outcome instanceof FilingOutcome.Rejected rejected) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ProblemResponse.builder()
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

        FilingOutcome.Indeterminate indeterminate = (FilingOutcome.Indeterminate) outcome;
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(ProblemResponse.builder()
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
    public ResponseEntity<FilingRecordResponse> bySubmissionId(@PathVariable String submissionId) {
        return service.findBySubmissionId(submissionId)
                .map(record -> ResponseEntity.ok(FilingRecordResponse.from(record)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Reconcile by idempotency key — the post-crash "did my filing go out?" question. */
    @GetMapping("/by-request/{clientRequestId}")
    public ResponseEntity<FilingRecordResponse> byClientRequestId(@PathVariable String clientRequestId) {
        return service.findByClientRequestId(clientRequestId)
                .map(record -> ResponseEntity.ok(FilingRecordResponse.from(record)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
