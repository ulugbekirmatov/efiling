package com.irs.mef.filing;


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
public class InMemoryJournal implements Journal {

    private final Map<String, SubmissionRecord> bySubmissionId = new HashMap<>();
    private final Map<String, String> submissionIdByRequestKey = new HashMap<>();
    private final Map<String, List<String>> submissionIdsByFilingIdentity = new HashMap<>();

    @Override
    public synchronized Reservation reserve(SubmissionRecord created) {
        String existingId = submissionIdByRequestKey.get(created.clientRequestId());
        if (existingId != null) {
            SubmissionRecord existing = bySubmissionId.get(existingId);
            if (existing != null && !existing.releasesIdempotencyKey()) {
                return new Reservation.AlreadyClaimed(existing);
            }
        }
        index(created);
        return new Reservation.Reserved(created);
    }

    @Override
    public synchronized void complete(SubmissionId submissionId, FilingOutcome outcome, Instant at) {
        SubmissionRecord updated = required(submissionId).completedWith(outcome, at);
        bySubmissionId.put(submissionId.value(), updated);
    }

    @Override
    public synchronized void abandon(SubmissionId submissionId, IrsFault fault, Instant at) {
        SubmissionRecord updated = required(submissionId).abandoned(fault, at);
        bySubmissionId.put(submissionId.value(), updated);
    }

    @Override
    public synchronized Optional<SubmissionRecord> findBySubmissionId(SubmissionId submissionId) {
        return Optional.ofNullable(bySubmissionId.get(submissionId.value()));
    }

    @Override
    public synchronized Optional<SubmissionRecord> findByClientRequestId(String clientRequestId) {
        return Optional.ofNullable(submissionIdByRequestKey.get(clientRequestId))
                .map(bySubmissionId::get);
    }

    @Override
    public synchronized List<SubmissionRecord> findFiledFor(Ein clientEin, FormSpec formType,
                                                                   TaxPeriod taxPeriod) {
        List<String> ids = submissionIdsByFilingIdentity
                .getOrDefault(filingIdentityKey(clientEin, formType, taxPeriod), List.of());
        List<SubmissionRecord> records = new ArrayList<>(ids.size());
        for (String id : ids) {
            records.add(bySubmissionId.get(id));
        }
        return records;
    }

    @Override
    public synchronized List<SubmissionRecord> findByClientId(String clientId) {
        List<SubmissionRecord> records = new ArrayList<>();
        for (SubmissionRecord record : bySubmissionId.values()) {
            if (record.clientId().equals(clientId)) {
                records.add(record);
            }
        }
        return records;
    }

    private SubmissionRecord required(SubmissionId submissionId) {
        SubmissionRecord record = bySubmissionId.get(submissionId.value());
        if (record == null) {
            throw new IllegalStateException("No journal row for submission id " + submissionId.value());
        }
        return record;
    }

    private void index(SubmissionRecord record) {
        bySubmissionId.put(record.submissionId().value(), record);
        submissionIdByRequestKey.put(record.clientRequestId(), record.submissionId().value());
        submissionIdsByFilingIdentity
                .computeIfAbsent(filingIdentityKey(record.clientEin(), record.formType(), record.taxPeriod()),
                        key -> new ArrayList<>())
                .add(record.submissionId().value());
    }

    static String filingIdentityKey(Ein clientEin, FormSpec formType, TaxPeriod taxPeriod) {
        return clientEin.value() + '|' + formType.code() + '|' + taxPeriod.begin() + '|' + taxPeriod.end();
    }
}
