package com.irs.mef.newsend.journal;

import com.irs.mef.newsend.domain.Ein;
import com.irs.mef.newsend.domain.FormType;
import com.irs.mef.newsend.domain.NewSendFault;
import com.irs.mef.newsend.domain.NewSendOutcome;
import com.irs.mef.newsend.domain.NewSendSubmissionId;
import com.irs.mef.newsend.domain.NewSendSubmissionRecord;
import com.irs.mef.newsend.domain.TaxPeriod;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * TEST-ONLY journal. It is a write-ahead log that forgets on process death, which is a
 * contradiction — never configure it where a real filing can happen.
 */
public class InMemoryNewSendSubmissionJournal implements NewSendSubmissionJournal {

    private final Map<String, NewSendSubmissionRecord> bySubmissionId = new HashMap<>();
    private final Map<String, String> submissionIdByRequestKey = new HashMap<>();
    private final Map<String, List<String>> submissionIdsByFilingIdentity = new HashMap<>();

    @Override
    public synchronized NewSendReservation reserve(NewSendSubmissionRecord created) {
        String existingId = submissionIdByRequestKey.get(created.clientRequestId());
        if (existingId != null) {
            NewSendSubmissionRecord existing = bySubmissionId.get(existingId);
            if (existing != null && !existing.releasesIdempotencyKey()) {
                return new NewSendReservation.AlreadyClaimed(existing);
            }
        }
        index(created);
        return new NewSendReservation.Reserved(created);
    }

    @Override
    public synchronized void complete(NewSendSubmissionId submissionId, NewSendOutcome outcome, Instant at) {
        NewSendSubmissionRecord updated = required(submissionId).completedWith(outcome, at);
        bySubmissionId.put(submissionId.value(), updated);
    }

    @Override
    public synchronized void abandon(NewSendSubmissionId submissionId, NewSendFault fault, Instant at) {
        NewSendSubmissionRecord updated = required(submissionId).abandoned(fault, at);
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
        List<String> ids = submissionIdsByFilingIdentity
                .getOrDefault(filingIdentityKey(clientEin, formType, taxPeriod), List.of());
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

    private void index(NewSendSubmissionRecord record) {
        bySubmissionId.put(record.submissionId().value(), record);
        submissionIdByRequestKey.put(record.clientRequestId(), record.submissionId().value());
        submissionIdsByFilingIdentity
                .computeIfAbsent(filingIdentityKey(record.clientEin(), record.formType(), record.taxPeriod()),
                        key -> new ArrayList<>())
                .add(record.submissionId().value());
    }

    static String filingIdentityKey(Ein clientEin, FormType formType, TaxPeriod taxPeriod) {
        return clientEin.value() + '|' + formType.code() + '|' + taxPeriod.begin() + '|' + taxPeriod.end();
    }
}
