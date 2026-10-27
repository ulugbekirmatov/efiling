package com.irs.mef.ats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irs.mef.ack.StoredAck;
import com.irs.mef.filing.ClientFiling;
import com.irs.mef.filing.FilingDesk;
import com.irs.mef.filing.FilingException;
import com.irs.mef.filing.FilingOutcome;
import com.irs.mef.filing.FilingReceipt;
import com.irs.mef.filing.FilingValidationException;
import com.irs.mef.filing.IrsFault;
import com.irs.mef.filing.Preflight;
import com.irs.mef.filing.RuleResult;
import com.irs.mef.filing.SubmissionId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * One live ATS send. Refuses unless the environment is ATS. Writes {@code target/ats-verdict.json}.
 */
public final class AtsRun {

    private static final int POLLS = 12;
    private static final long PAUSE_MILLIS = 30_000L;
    private static final Path VERDICT = Path.of("target", "ats-verdict.json");

    private final FilingDesk desk;
    private final RejectLedger ledger;
    private final String environment;

    public AtsRun(FilingDesk desk, RejectLedger ledger, String environment) {
        this.desk = desk;
        this.ledger = ledger;
        this.environment = environment;
    }

    public AtsVerdict run(AtsScenario scenario) throws InterruptedException, IOException {
        if (!"ATS".equals(environment)) {
            return write(new AtsVerdict.NotSent(new FilingOutcome.Indeterminate(new IrsFault(
                    "NEWSEND_ENVIRONMENT",
                    "AtsRun refuses environment " + environment + "; ATS only",
                    null, null, null))));
        }
        Optional<RejectLedger.Blocked> blocked = ledger.blockFor(scenario);
        if (blocked.isPresent()) {
            return write(new AtsVerdict.Blocked(blocked.get()));
        }
        String body = Files.readString(scenario.clientBody());
        ClientFiling filing = ClientFiling.parse(scenario.clientId(), body);
        Preflight preflight;
        try {
            preflight = desk.preflight(filing);
        } catch (FilingValidationException e) {
            return write(new AtsVerdict.PreflightFailed(List.of(
                    new RuleResult("preflight", false, e.getMessage()))));
        }
        if (!preflight.failures().isEmpty()) {
            return write(new AtsVerdict.PreflightFailed(preflight.failures()));
        }
        FilingReceipt receipt;
        try {
            receipt = desk.file(filing);
        } catch (FilingValidationException e) {
            return write(new AtsVerdict.PreflightFailed(List.of(
                    new RuleResult("preflight", false, e.getMessage()))));
        } catch (FilingException e) {
            return write(new AtsVerdict.NotSent(new FilingOutcome.Indeterminate(new IrsFault(
                    e.errorCode(), e.getMessage(), null, null, null))));
        }
        if (!(receipt.outcome() instanceof FilingOutcome.Transmitted)) {
            return write(new AtsVerdict.NotSent(receipt.outcome()));
        }
        SubmissionId id = receipt.id();
        for (int attempt = 1; attempt <= POLLS; attempt++) {
            Optional<StoredAck> ack = desk.ack(id);
            if (ack.isPresent()) {
                return write(fromAck(id, ack.get()));
            }
            if (attempt < POLLS) {
                Thread.sleep(PAUSE_MILLIS);
            }
        }
        return write(new AtsVerdict.NoAck(id));
    }

    private static AtsVerdict fromAck(SubmissionId id, StoredAck ack) {
        if ("Accepted".equals(ack.ackType())) {
            return new AtsVerdict.Accepted(id);
        }
        return new AtsVerdict.Rejected(id, Set.copyOf(ack.errorCodes()));
    }

    private static AtsVerdict write(AtsVerdict verdict) throws IOException {
        if (VERDICT.getParent() != null) {
            Files.createDirectories(VERDICT.getParent());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("kind", verdict.getClass().getSimpleName());
        body.put("exitCode", verdict.exitCode());
        if (verdict instanceof AtsVerdict.Accepted accepted) {
            body.put("submissionId", accepted.id().value());
        } else if (verdict instanceof AtsVerdict.Rejected rejected) {
            body.put("submissionId", rejected.id().value());
            body.put("codes", rejected.codes());
        } else if (verdict instanceof AtsVerdict.NoAck noAck) {
            body.put("submissionId", noAck.id().value());
        } else if (verdict instanceof AtsVerdict.NotSent notSent) {
            body.put("outcome", notSent.why().getClass().getSimpleName());
        } else if (verdict instanceof AtsVerdict.Blocked blocked) {
            body.put("submissionId", blocked.why().lastSend().value());
            body.put("codes", blocked.why().unreproducedCodes());
        } else if (verdict instanceof AtsVerdict.PreflightFailed failed) {
            body.put("failures", failed.failures().stream().map(RuleResult::irsCode).toList());
        }
        new ObjectMapper().writeValue(VERDICT.toFile(), body);
        return verdict;
    }
}
