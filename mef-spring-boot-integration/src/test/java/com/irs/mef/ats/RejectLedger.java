package com.irs.mef.ats;

import com.irs.mef.ack.AckStore;
import com.irs.mef.ack.StoredAck;
import com.irs.mef.filing.Journal;
import com.irs.mef.filing.LocalRules;
import com.irs.mef.filing.SubmissionId;
import com.irs.mef.filing.SubmissionRecord;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public final class RejectLedger {

    private final Journal journal;
    private final AckStore ackStore;

    public RejectLedger(Journal journal, AckStore ackStore) {
        this.journal = journal;
        this.ackStore = ackStore;
    }

    public Optional<Blocked> blockFor(AtsScenario scenario) {
        Optional<SubmissionRecord> latest = journal.findByClientId(scenario.clientId()).stream()
                .max(Comparator.comparing(SubmissionRecord::createdAt)
                        .thenComparing(record -> record.submissionId().value()));
        if (latest.isEmpty()) {
            return Optional.empty();
        }
        SubmissionId id = latest.get().submissionId();
        Optional<StoredAck> ack = ackStore.find(id.value());
        if (ack.isEmpty() || !"Rejected".equals(ack.get().ackType())) {
            return Optional.empty();
        }
        Set<String> unreproduced = new LinkedHashSet<>();
        for (String code : ack.get().errorCodes()) {
            if (code != null && !LocalRules.covers(code)) {
                unreproduced.add(code);
            }
        }
        if (unreproduced.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Blocked(id, Set.copyOf(unreproduced)));
    }

    public record Blocked(SubmissionId lastSend, Set<String> unreproducedCodes) {
        public Blocked {
            unreproducedCodes = Set.copyOf(unreproducedCodes);
        }
    }
}
