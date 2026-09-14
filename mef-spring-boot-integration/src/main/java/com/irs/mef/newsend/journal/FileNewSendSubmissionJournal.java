package com.irs.mef.newsend.journal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irs.mef.newsend.domain.Ein;
import com.irs.mef.newsend.domain.FormType;
import com.irs.mef.newsend.domain.NewSendFault;
import com.irs.mef.newsend.domain.NewSendOutcome;
import com.irs.mef.newsend.domain.NewSendReceipt;
import com.irs.mef.newsend.domain.NewSendSubmissionId;
import com.irs.mef.newsend.domain.NewSendSubmissionRecord;
import com.irs.mef.newsend.domain.NewSendState;
import com.irs.mef.newsend.domain.TaxPeriod;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DEFAULT journal. Append-only JSON Lines, one line per state transition, fsync'd before return —
 * crash-safe by construction (append + force, no in-place mutation) and trivially greppable for
 * an auditor. A real database implements the same port later without a redesign.
 *
 * Startup replay: last line per submission id wins. Any row still CREATED at load time is a crash
 * mid-flight and is promoted to INDETERMINATE — a crash must never silently become "never filed".
 *
 * Writes are serialised on the instance lock; volume is quarterly-batch scale, not OLTP.
 */
@Slf4j
public class FileNewSendSubmissionJournal implements NewSendSubmissionJournal {

    private final ObjectMapper objectMapper;
    private final FileChannel channel;

    private final Map<String, NewSendSubmissionRecord> bySubmissionId = new HashMap<>();
    private final Map<String, String> submissionIdByRequestKey = new HashMap<>();
    private final Map<String, List<String>> submissionIdsByFilingIdentity = new HashMap<>();

    public FileNewSendSubmissionJournal(Path path, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            List<NewSendSubmissionRecord> replayed = replay(path);
            this.channel = FileChannel.open(path,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            for (NewSendSubmissionRecord record : replayed) {
                index(record);
            }
            promoteCrashedRows();
            log.info("NewSend journal loaded from {}: {} submission(s)", path, bySubmissionId.size());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot open NewSend journal at " + path, e);
        }
    }

    @Override
    public synchronized NewSendReservation reserve(NewSendSubmissionRecord created) {
        String existingId = submissionIdByRequestKey.get(created.clientRequestId());
        if (existingId != null) {
            NewSendSubmissionRecord existing = bySubmissionId.get(existingId);
            if (existing != null && !existing.releasesIdempotencyKey()) {
                return new NewSendReservation.AlreadyClaimed(existing);
            }
        }
        append(created);
        index(created);
        return new NewSendReservation.Reserved(created);
    }

    @Override
    public synchronized void complete(NewSendSubmissionId submissionId, NewSendOutcome outcome, Instant at) {
        NewSendSubmissionRecord updated = required(submissionId).completedWith(outcome, at);
        append(updated);
        bySubmissionId.put(submissionId.value(), updated);
    }

    @Override
    public synchronized void abandon(NewSendSubmissionId submissionId, NewSendFault fault, Instant at) {
        NewSendSubmissionRecord updated = required(submissionId).abandoned(fault, at);
        append(updated);
        bySubmissionId.put(submissionId.value(), updated);
    }

    @Override
    public synchronized Optional<NewSendSubmissionRecord> findBySubmissionId(NewSendSubmissionId submissionId) {
        return Optional.ofNullable(bySubmissionId.get(submissionId.value()));
    }

    @Override
    public synchronized Optional<NewSendSubmissionRecord> findByClientRequestId(String clientRequestId) {
        return Optional.ofNullable(submissionIdByRequestKey.get(clientRequestId))
                .map(bySubmissionId::get);
    }

    @Override
    public synchronized List<NewSendSubmissionRecord> findFiledFor(Ein clientEin, FormType formType,
                                                                   TaxPeriod taxPeriod) {
        List<String> ids = submissionIdsByFilingIdentity.getOrDefault(
                InMemoryNewSendSubmissionJournal.filingIdentityKey(clientEin, formType, taxPeriod), List.of());
        List<NewSendSubmissionRecord> records = new ArrayList<>(ids.size());
        for (String id : ids) {
            records.add(bySubmissionId.get(id));
        }
        return records;
    }

    private NewSendSubmissionRecord required(NewSendSubmissionId submissionId) {
        NewSendSubmissionRecord record = bySubmissionId.get(submissionId.value());
        if (record == null) {
            throw new IllegalStateException("No journal row for submission id " + submissionId.value());
        }
        return record;
    }

    private List<NewSendSubmissionRecord> replay(Path path) throws IOException {
        if (!Files.exists(path)) {
            return List.of();
        }
        Map<String, NewSendSubmissionRecord> lastPerSubmission = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            NewSendSubmissionRecord record = fromLine(objectMapper.readValue(line, JournalLine.class));
            lastPerSubmission.put(record.submissionId().value(), record);
        }
        return new ArrayList<>(lastPerSubmission.values());
    }

    private void promoteCrashedRows() {
        for (Map.Entry<String, NewSendSubmissionRecord> entry : bySubmissionId.entrySet()) {
            NewSendSubmissionRecord record = entry.getValue();
            if (record.state() == NewSendState.CREATED) {
                log.error("NewSend journal: submission {} was mid-flight at shutdown — promoting to "
                        + "INDETERMINATE; reconcile via acks before refiling", record.submissionId().value());
                NewSendSubmissionRecord promoted = record.completedWith(
                        new NewSendOutcome.Indeterminate(NewSendFault.crashRecovery()), Instant.now());
                append(promoted);
                entry.setValue(promoted);
            }
        }
    }

    private void index(NewSendSubmissionRecord record) {
        bySubmissionId.put(record.submissionId().value(), record);
        submissionIdByRequestKey.put(record.clientRequestId(), record.submissionId().value());
        List<String> identityList = submissionIdsByFilingIdentity.computeIfAbsent(
                InMemoryNewSendSubmissionJournal.filingIdentityKey(
                        record.clientEin(), record.formType(), record.taxPeriod()),
                key -> new ArrayList<>());
        if (!identityList.contains(record.submissionId().value())) {
            identityList.add(record.submissionId().value());
        }
    }

    private void append(NewSendSubmissionRecord record) {
        try {
            byte[] bytes = (objectMapper.writeValueAsString(toLine(record)) + "\n")
                    .getBytes(StandardCharsets.UTF_8);
            channel.write(ByteBuffer.wrap(bytes));
            channel.force(true);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "NewSend journal write failed for " + record.submissionId().value()
                            + " — refusing to proceed without durable evidence", e);
        }
    }

    private static JournalLine toLine(NewSendSubmissionRecord record) {
        NewSendReceipt receipt = record.receipt();
        NewSendFault fault = record.fault();
        return new JournalLine(
                record.submissionId().value(),
                record.clientRequestId(),
                record.clientId(),
                record.clientEin().value(),
                record.formType().code(),
                record.taxPeriod().begin().toString(),
                record.taxPeriod().end().toString(),
                record.returnXmlSha256(),
                record.environment(),
                record.state().name(),
                record.createdAt().toString(),
                record.completedAt() != null ? record.completedAt().toString() : null,
                receipt != null ? receipt.depositId() : null,
                receipt != null ? receipt.receiptTimestamp().toString() : null,
                receipt != null ? receipt.receiptTimestampSource().name() : null,
                receipt != null ? receipt.receiptCount() : null,
                fault != null ? fault.code() : null,
                fault != null ? fault.message() : null,
                fault != null ? fault.sdkExceptionClass() : null,
                fault != null ? fault.detail() : null,
                fault != null ? fault.logHint() : null);
    }

    private static NewSendSubmissionRecord fromLine(JournalLine line) {
        NewSendSubmissionId submissionId = new NewSendSubmissionId(line.submissionId());
        NewSendReceipt receipt = line.depositId() == null ? null : new NewSendReceipt(
                line.depositId(),
                submissionId,
                Instant.parse(line.receiptTimestamp()),
                NewSendReceipt.TimestampSource.valueOf(line.receiptTimestampSource()),
                line.receiptCount() != null ? line.receiptCount() : 0);
        NewSendFault fault = line.faultCode() == null ? null : new NewSendFault(
                line.faultCode(), line.faultMessage(), line.faultSdkExceptionClass(),
                line.faultDetail(), line.faultLogHint());
        return new NewSendSubmissionRecord(
                submissionId,
                line.clientRequestId(),
                line.clientId(),
                new Ein(line.clientEin()),
                FormType.parse(line.formType()),
                new TaxPeriod(LocalDate.parse(line.taxPeriodBegin()), LocalDate.parse(line.taxPeriodEnd())),
                line.returnXmlSha256(),
                line.environment(),
                NewSendState.valueOf(line.state()),
                Instant.parse(line.createdAt()),
                line.completedAt() != null ? Instant.parse(line.completedAt()) : null,
                receipt,
                fault);
    }

    /** Flat wire shape for one JSONL line — strings only, so the file needs no Jackson modules. */
    record JournalLine(
            String submissionId,
            String clientRequestId,
            String clientId,
            String clientEin,
            String formType,
            String taxPeriodBegin,
            String taxPeriodEnd,
            String returnXmlSha256,
            String environment,
            String state,
            String createdAt,
            String completedAt,
            String depositId,
            String receiptTimestamp,
            String receiptTimestampSource,
            Integer receiptCount,
            String faultCode,
            String faultMessage,
            String faultSdkExceptionClass,
            String faultDetail,
            String faultLogHint) {
    }
}
