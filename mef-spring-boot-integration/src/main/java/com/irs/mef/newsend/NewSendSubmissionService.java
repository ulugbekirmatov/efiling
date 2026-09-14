package com.irs.mef.newsend;

import com.irs.mef.config.MefSdkConfig;
import com.irs.mef.exception.MefException;
import com.irs.mef.newsend.domain.Efin;
import com.irs.mef.newsend.domain.NewSendFault;
import com.irs.mef.newsend.domain.NewSendFiling;
import com.irs.mef.newsend.domain.NewSendOutcome;
import com.irs.mef.newsend.domain.NewSendState;
import com.irs.mef.newsend.domain.NewSendSubmissionId;
import com.irs.mef.newsend.domain.NewSendSubmissionRecord;
import com.irs.mef.newsend.domain.NewSendSubmitCommand;
import com.irs.mef.newsend.error.NewSendBusyException;
import com.irs.mef.newsend.error.NewSendConfigurationException;
import com.irs.mef.newsend.error.NewSendDuplicateException;
import com.irs.mef.newsend.error.NewSendNotLoggedInException;
import com.irs.mef.newsend.error.NewSendValidationException;
import com.irs.mef.newsend.gateway.NewSendCompositionException;
import com.irs.mef.newsend.gateway.NewSendTransmitGateway;
import com.irs.mef.newsend.journal.NewSendSubmissionJournal;
import com.irs.mef.newsend.journal.NewSendSubmissionJournal.NewSendReservation;
import com.irs.mef.newsend.validate.NewSendReturnXmlValidator;
import com.irs.mef.service.MefClientService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Orchestrates one filing. Public surface: {@link #submit} plus two reads. Behind it sit
 * idempotency, write-ahead journaling, submission-id minting, XSD pre-validation, session
 * serialisation, and receipt matching.
 *
 * DELIBERATELY ABSENT: retry. SendSubmissions is not idempotent at the IRS; a retry is a
 * duplicate federal filing. The codebase's RetryTemplate is consciously not wired here.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NewSendSubmissionService {

    private final MefClientService mefClientService;
    private final MefSdkConfig mefSdkConfig;
    private final NewSendProperties properties;
    private final NewSendSubmissionJournal journal;
    private final NewSendReturnXmlValidator returnXmlValidator;
    private final NewSendTransmitGateway gateway;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /**
     * The single ServiceContext in MefClientService is not thread-safe and the IRS enforces a
     * concurrent-session limit, so transmits are serialised rather than hoped about. Per-actor
     * sessions are impossible (a second ServiceContext invalidates the first), so we serialise
     * explicitly, with a bounded wait and an honest 503.
     * Known gap: the legacy SubmissionService shares the same session and does not take this lock.
     */
    private final ReentrantLock sessionLock = new ReentrantLock(true);

    /** Warn at boot (not fail — the app must still start for login/diagnostics without an EFIN). */
    @PostConstruct
    void warnOnUnusableEfin() {
        try {
            configuredEfin();
        } catch (NewSendConfigurationException e) {
            log.warn("NewSend submissions will fail until fixed: {}", e.getMessage());
        }
    }

    /**
     * File one 94x return. Order is load-bearing:
     * 1 replay/conflict probe, 2 duplicate-period guard, 3 validate, 4 mint id, 5 reserve
     * (durable, pre-wire), 6 lock, 7 transmit, 8 complete. Steps 1-5 can fail safely; from
     * step 7 on, the journal row already exists on disk.
     *
     * @throws NewSendNotLoggedInException 401 — nothing was sent
     * @throws NewSendValidationException  400 — nothing was sent
     * @throws NewSendDuplicateException   409 — key or (EIN, form, period) collision
     * @throws NewSendBusyException        503 — session not acquired within the configured wait
     */
    public NewSendSubmitResult submit(NewSendSubmitCommand command) {
        if (!mefClientService.isLoggedIn()) {
            throw new NewSendNotLoggedInException();
        }
        Efin efin = configuredEfin();
        String environment = mefSdkConfig.getEnvironment();
        if (command.returnXml().length() > properties.getMaxReturnXmlChars()) {
            throw new NewSendValidationException("returnXml",
                    "returnXml exceeds " + properties.getMaxReturnXmlChars() + " characters");
        }

        Optional<NewSendSubmissionRecord> priorClaim = journal.findByClientRequestId(command.clientRequestId());
        if (priorClaim.isPresent() && !priorClaim.get().releasesIdempotencyKey()) {
            return replayOrConflict(priorClaim.get(), command, environment);
        }

        guardDuplicatePeriod(command, environment);
        returnXmlValidator.validate(command.formType(), command.returnXml());

        Instant now = clock.instant();
        NewSendSubmissionId submissionId =
                NewSendSubmissionId.generate(efin, now, properties.processingZoneId(), random);
        NewSendFiling filing = new NewSendFiling(submissionId, command, now);

        NewSendReservation reservation = journal.reserve(NewSendSubmissionRecord.created(filing, environment));
        if (reservation instanceof NewSendReservation.AlreadyClaimed alreadyClaimed) {
            // Lost a race on the same key between the probe and the reservation.
            return replayOrConflict(alreadyClaimed.existing(), command, environment);
        }

        return transmitReserved(filing, environment);
    }

    public Optional<NewSendSubmissionRecord> findBySubmissionId(String rawSubmissionId) {
        return journal.findBySubmissionId(new NewSendSubmissionId(rawSubmissionId));
    }

    public Optional<NewSendSubmissionRecord> findByClientRequestId(String clientRequestId) {
        return journal.findByClientRequestId(clientRequestId);
    }

    private NewSendSubmitResult transmitReserved(NewSendFiling filing, String environment) {
        NewSendSubmissionId submissionId = filing.submissionId();
        boolean locked;
        try {
            locked = sessionLock.tryLock(properties.getSessionWaitSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            journal.abandon(submissionId, NewSendFault.lockTimeout(), clock.instant());
            throw new NewSendBusyException(properties.getSessionWaitSeconds());
        }
        if (!locked) {
            journal.abandon(submissionId, NewSendFault.lockTimeout(), clock.instant());
            throw new NewSendBusyException(properties.getSessionWaitSeconds());
        }
        try (MDC.MDCCloseable ignored = MDC.putCloseable("submissionId", submissionId.value())) {
            Object serviceContext;
            try {
                serviceContext = mefClientService.getCurrentServiceContext();
            } catch (MefException e) {
                journal.abandon(submissionId, NewSendFault.sessionLost(e), clock.instant());
                throw new NewSendNotLoggedInException();
            }

            NewSendOutcome outcome;
            try {
                outcome = gateway.transmit(serviceContext, filing);
            } catch (NewSendCompositionException e) {
                // Provably pre-wire: release the key so a corrected request can reuse it.
                journal.abandon(submissionId, e.fault(), clock.instant());
                throw new NewSendValidationException("returnXml",
                        "The submission could not be composed: " + e.getMessage());
            }

            boolean journalDurable = completeQuietly(submissionId, outcome);
            logOutcome(filing, outcome, environment, journalDurable);
            return new NewSendSubmitResult(submissionId, outcome, false, journalDurable);
        } finally {
            sessionLock.unlock();
        }
    }

    /**
     * The idempotency protocol for an existing claim on this key.
     * Same payload + TRANSMITTED replays the stored receipt without any network call;
     * everything else is a 409 that names the prior submission id.
     */
    private NewSendSubmitResult replayOrConflict(NewSendSubmissionRecord existing,
                                                 NewSendSubmitCommand command,
                                                 String environment) {
        List<String> conflicting = List.of(existing.submissionId().value());
        if (!existing.environment().equals(environment)) {
            throw new NewSendDuplicateException("NEWSEND_ENVIRONMENT_MISMATCH",
                    "Idempotency key was used in environment " + existing.environment()
                            + "; current environment is " + environment, conflicting);
        }
        boolean samePayload = existing.returnXmlSha256().equals(command.returnXmlSha256())
                && existing.clientEin().equals(command.clientEin())
                && existing.formType() == command.formType()
                && existing.taxPeriod().equals(command.taxPeriod());
        if (!samePayload) {
            throw new NewSendDuplicateException("NEWSEND_KEY_REUSED",
                    "Idempotency key was already used for submission " + existing.submissionId().value()
                            + " with a different payload. Use a new key for a new filing.", conflicting);
        }
        NewSendState state = existing.state();
        if (state == NewSendState.TRANSMITTED) {
            log.info("Idempotent replay of {} (key {}): nothing re-sent to the IRS",
                    existing.submissionId().value(), command.clientRequestId());
            return new NewSendSubmitResult(existing.submissionId(),
                    new NewSendOutcome.Transmitted(existing.receipt()), true, true);
        }
        if (state == NewSendState.REJECTED) {
            throw new NewSendDuplicateException("NEWSEND_KEY_REJECTED_PREVIOUSLY",
                    "This key's filing was rejected by the IRS as submission " + existing.submissionId().value()
                            + ". Correct the return and file under a NEW key.", conflicting);
        }
        // CREATED (another thread is mid-flight) or INDETERMINATE (outcome unknown).
        throw new NewSendDuplicateException("NEWSEND_KEY_UNRESOLVED",
                "This key's filing (submission " + existing.submissionId().value() + ") is in state " + state
                        + ". Do NOT resubmit; reconcile via status/acks for that submission id first.",
                conflicting);
    }

    /** Catches the human duplicate: same quarter re-run under a fresh key. Override is explicit per request. */
    private void guardDuplicatePeriod(NewSendSubmitCommand command, String environment) {
        if (command.allowDuplicatePeriod()) {
            return;
        }
        List<String> blocking = journal
                .findFiledFor(command.clientEin(), command.formType(), command.taxPeriod())
                .stream()
                .filter(record -> record.environment().equals(environment))
                .filter(record -> record.state() == NewSendState.CREATED
                        || record.state() == NewSendState.TRANSMITTED
                        || record.state() == NewSendState.INDETERMINATE)
                .map(record -> record.submissionId().value())
                .toList();
        if (!blocking.isEmpty()) {
            throw new NewSendDuplicateException("NEWSEND_PERIOD_ALREADY_FILED",
                    command.formType().code() + " for EIN " + command.clientEin().masked() + ", period "
                            + command.taxPeriod().begin() + ".." + command.taxPeriod().end()
                            + " was already transmitted or is unresolved. Set allowDuplicatePeriod=true "
                            + "to deliberately file again (e.g. a correction).", blocking);
        }
    }

    /**
     * If the journal write after a successful transmit fails, still return the receipt
     * (journalDurable=false) — withholding a deposit id the caller cannot otherwise recover
     * would be the worse failure.
     */
    private boolean completeQuietly(NewSendSubmissionId submissionId, NewSendOutcome outcome) {
        try {
            journal.complete(submissionId, outcome, clock.instant());
            return true;
        } catch (RuntimeException e) {
            log.error("Journal write FAILED after transmit of {} — the outcome below is NOT persisted; "
                    + "record it manually: {}", submissionId.value(), outcome, e);
            return false;
        }
    }

    private void logOutcome(NewSendFiling filing, NewSendOutcome outcome, String environment,
                            boolean journalDurable) {
        if (outcome instanceof NewSendOutcome.Transmitted transmitted) {
            log.info("TRANSMITTED {} ({} for EIN {}, {}): deposit id {}, receipts {}, durable={}",
                    filing.submissionId().value(), filing.command().formType().code(),
                    filing.command().clientEin().masked(), environment,
                    transmitted.receipt().depositId(), transmitted.receipt().receiptCount(), journalDurable);
        } else if (outcome instanceof NewSendOutcome.Rejected rejected) {
            log.warn("REJECTED {} by the IRS: {} ({})", filing.submissionId().value(),
                    rejected.fault().message(), rejected.fault().logHint());
        } else {
            NewSendOutcome.Indeterminate indeterminate = (NewSendOutcome.Indeterminate) outcome;
            log.error("INDETERMINATE {} — the IRS MAY hold this filing. Do NOT resubmit. {} ({})",
                    filing.submissionId().value(), indeterminate.fault().message(),
                    indeterminate.fault().logHint());
        }
    }

    private Efin configuredEfin() {
        String rawEfin = mefSdkConfig.getAuthentication() != null
                ? mefSdkConfig.getAuthentication().getEfin()
                : null;
        try {
            return new Efin(rawEfin);
        } catch (NewSendValidationException e) {
            throw new NewSendConfigurationException(
                    "MEF_EFIN is missing or not exactly 6 digits — set it before filing");
        }
    }
}
