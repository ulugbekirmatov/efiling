package com.irs.mef.web;

import com.irs.mef.filing.ClientFiling;
import com.irs.mef.filing.FilingDesk;
import com.irs.mef.filing.FilingOutcome;
import com.irs.mef.filing.FilingReceipt;
import com.irs.mef.filing.SubmissionRecord;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Instant;

/**
 * Bind, parse, delegate. No SDK type and no business rule appears here.
 */
@RestController
@RequestMapping("/mef/filings")
@RequiredArgsConstructor
@Validated
public class FilingController {

    private final FilingDesk desk;

    /** File one client body. 201 on first transmission, 200 when the same bytes were already journaled. */
    @PostMapping
    public ResponseEntity<?> file(@Valid @RequestBody FilingRequest request) {
        FilingReceipt receipt = desk.file(ClientFiling.parse(
                request.clientId(), request.clientBody(), request.allowDuplicatePeriod()));
        FilingOutcome outcome = receipt.outcome();

        if (outcome instanceof FilingOutcome.Transmitted) {
            SubmissionRecord record = desk.findBySubmissionId(receipt.id().value())
                    .orElseThrow(() -> new IllegalStateException(
                            "Transmitted submission missing from journal: " + receipt.id().value()));
            FilingResponse body = FilingResponse.from(record, receipt);
            if (receipt.replay()) {
                return ResponseEntity.ok(body);
            }
            return ResponseEntity
                    .created(ServletUriComponentsBuilder.fromCurrentRequest()
                            .path("/{id}").buildAndExpand(receipt.id().value()).toUri())
                    .body(body);
        }

        if (outcome instanceof FilingOutcome.Rejected rejected) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ProblemResponse.builder()
                    .timestamp(Instant.now())
                    .status(HttpStatus.BAD_GATEWAY.value())
                    .errorCode(rejected.fault().code())
                    .message("The IRS refused the submission; nothing was filed. Correct the return "
                            + "and resubmit. " + rejected.fault().message())
                    .submissionId(receipt.id().value())
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
                .submissionId(receipt.id().value())
                .resubmitSafe(false)
                .reconcileWith("GET /api/mef/filings/" + receipt.id().value())
                .logHint(indeterminate.fault().logHint())
                .build());
    }

    /** Local journal record by submission id. Not live IRS status. */
    @GetMapping("/{submissionId}")
    public ResponseEntity<FilingRecordResponse> bySubmissionId(@PathVariable String submissionId) {
        return desk.findBySubmissionId(submissionId)
                .map(record -> ResponseEntity.ok(FilingRecordResponse.from(record)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
