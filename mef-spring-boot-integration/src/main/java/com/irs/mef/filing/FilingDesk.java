package com.irs.mef.filing;

import com.irs.mef.ack.AckStore;
import com.irs.mef.ack.StoredAck;
import com.irs.mef.config.MefSdkConfig;
import com.irs.mef.filing.Journal.Reservation;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * The filing pipeline. {@link #preflight} is offline. {@link #file} is the only send.
 * {@link #ack} is the only GetAck. There is no retry: SendSubmissions is not idempotent at the IRS.
 */
@Service
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class FilingDesk {

    private final IrsSession session;
    private final MefSdkConfig mefSdkConfig;
    private final Originator originator;
    private final FilingProperties properties;
    private final Journal journal;
    private final ReturnValidator returnXmlValidator;
    private final TransmitGateway gateway;
    private final AckStore ackStore;
    private final Clock clock;
    private final IrsWire wire;
    private final SecureRandom random = new SecureRandom();

    /**
     * Offline. Composes, mints, renders the manifest, XSD-validates the return and the manifest,
     * builds the SDK archive, and runs every {@link LocalRule}. Never touches {@link IrsSession}.
     */
    public Preflight preflight(ClientFiling filing) {
        if (filing.form().schema() instanceof SchemaBinding.Pending pending) {
            throw new FilingValidationException("form", pending.why());
        }
        if (filing.clientBodyXml().length() > properties.getMaxReturnXmlChars()) {
            throw new FilingValidationException("clientBody",
                    "clientBody exceeds " + properties.getMaxReturnXmlChars() + " characters");
        }
        ReturnComposer composed = ReturnComposer.compose(originator, filing.body(), clock);
        if (composed.xml().length() > properties.getMaxReturnXmlChars()) {
            throw new FilingValidationException("returnXml",
                    "returnXml exceeds " + properties.getMaxReturnXmlChars() + " characters");
        }
        Instant now = clock.instant();
        SubmissionId id = SubmissionId.mint(originator, now, Efile.PROCESSING_ZONE, random);
        SubmitCommand command = new SubmitCommand(
                contentKey(mefSdkConfig.getEnvironment(), filing),
                filing.clientId().value(),
                filing.filer(),
                filing.form(),
                filing.period(),
                composed.xml());
        Filing prepared = new Filing(id, command, now);
        String manifest = ManifestXml.render(prepared);
        returnXmlValidator.validate(filing.form(), composed.xml());
        String schemaReport = ManifestSchema.validate(manifest);
        Preflight draft = Preflight.of(filing, prepared, manifest);
        byte[] archive;
        try {
            archive = wire.buildArchive(draft);
        } catch (CompositionException e) {
            throw new FilingValidationException("archive",
                    "submission archive could not be built: " + e.getMessage());
        }
        return draft.finish(archive, LocalRules.check(draft), schemaReport);
    }

    /**
     * File one client body. TRANSMITTED and INDETERMINATE replay the journaled receipt and do
     * not call the IRS. REJECTED releases the content key, so the same bytes reserve a new
     * submission id and send again. A changed byte is a new filing.
     */
    public FilingReceipt file(ClientFiling filing) {
        String environment = mefSdkConfig.getEnvironment();
        Optional<SubmissionRecord> prior = journal.findByClientRequestId(contentKey(environment, filing));
        if (prior.isPresent() && !prior.get().releasesIdempotencyKey()) {
            return replay(prior.get(), environment);
        }

        Preflight preflight = preflight(filing);
        if (!preflight.failures().isEmpty()) {
            throw new FilingValidationException("preflight",
                    "local rules failed: " + String.join(", ",
                            preflight.failures().stream().map(RuleResult::irsCode).toList()));
        }
        guardDuplicatePeriod(preflight.prepared(), environment, filing.allowDuplicatePeriod());

        Reservation reservation = journal.reserve(
                SubmissionRecord.created(preflight.prepared(), environment));
        if (reservation instanceof Reservation.AlreadyClaimed alreadyClaimed) {
            return replay(alreadyClaimed.existing(), environment);
        }
        return transmit(preflight, environment);
    }

    /** GetAck for one submission. Records the ack. Safe to call twice. */
    public Optional<StoredAck> ack(SubmissionId id) {
        Optional<StoredAck> found = session.call(ctx -> gateway.getAck(ctx, id.value()));
        found.ifPresent(ackStore::record);
        return found;
    }

    public Optional<SubmissionRecord> findBySubmissionId(String rawSubmissionId) {
        return journal.findBySubmissionId(new SubmissionId(rawSubmissionId));
    }

    private FilingReceipt transmit(Preflight preflight, String environment) {
        SubmissionId submissionId = preflight.id();
        try (MDC.MDCCloseable ignored = MDC.putCloseable("submissionId", submissionId.value())) {
            FilingOutcome outcome;
            try {
                outcome = session.call(ctx -> gateway.transmit(ctx, preflight.cleared()));
            } catch (FilingBusyException busy) {
                journal.abandon(submissionId, IrsFault.lockTimeout(), clock.instant());
                throw busy;
            } catch (CompositionException e) {
                journal.abandon(submissionId, e.fault(), clock.instant());
                throw new FilingValidationException("clientBody",
                        "The submission could not be composed: " + e.getMessage());
            } catch (IrsLoginException e) {
                journal.abandon(submissionId, IrsFault.sessionLost(e), clock.instant());
                throw e;
            }

            boolean journalDurable = completeQuietly(submissionId, outcome);
            logOutcome(preflight.prepared(), outcome, environment, journalDurable);
            return new FilingReceipt(
                    submissionId,
                    preflight.filing().clientId(),
                    outcome,
                    journalDurable,
                    false);
        }
    }

    /**
     * Same bytes as a journaled filing. Returns that receipt and does not call the IRS.
     * CREATED (another thread is mid-flight) is the one state that is not yet a receipt.
     */
    private FilingReceipt replay(SubmissionRecord existing, String environment) {
        if (!existing.environment().equals(environment)) {
            throw new FilingDuplicateException("NEWSEND_ENVIRONMENT_MISMATCH",
                    "These bytes were filed in environment " + existing.environment()
                            + "; current environment is " + environment,
                    List.of(existing.submissionId().value()));
        }
        if (existing.state() == JournalState.CREATED) {
            throw new FilingDuplicateException("NEWSEND_KEY_UNRESOLVED",
                    "A filing of these bytes (submission " + existing.submissionId().value()
                            + ") is in state " + existing.state()
                            + ". Do NOT resubmit; reconcile that submission id first.",
                    List.of(existing.submissionId().value()));
        }
        log.info("Idempotent replay of {} ({}): nothing re-sent to the IRS",
                existing.submissionId().value(), existing.state());
        return new FilingReceipt(
                existing.submissionId(),
                new ClientId(existing.clientId()),
                outcomeOf(existing),
                true,
                true);
    }

    private static FilingOutcome outcomeOf(SubmissionRecord existing) {
        return switch (existing.state()) {
            case TRANSMITTED -> new FilingOutcome.Transmitted(existing.receipt());
            case REJECTED -> new FilingOutcome.Rejected(existing.fault());
            case INDETERMINATE -> new FilingOutcome.Indeterminate(existing.fault());
            case CREATED, ABANDONED -> throw new IllegalStateException(
                    "No receipt for " + existing.submissionId().value() + " in state " + existing.state());
        };
    }

    /**
     * Same EIN, form, and period already transmitted or still unresolved.
     * A SOAP rejection does not block. A TRANSMITTED row whose stored ack is Rejected does not
     * block either: the IRS filed nothing. {@code allowDuplicatePeriod} skips the guard.
     */
    private void guardDuplicatePeriod(Filing filing, String environment, boolean allowDuplicatePeriod) {
        if (allowDuplicatePeriod) {
            return;
        }
        SubmitCommand command = filing.command();
        List<String> blocking = journal
                .findFiledFor(command.clientEin(), command.formType(), command.taxPeriod())
                .stream()
                .filter(record -> record.environment().equals(environment))
                .filter(this::blocksPeriod)
                .map(record -> record.submissionId().value())
                .toList();
        if (!blocking.isEmpty()) {
            throw new FilingDuplicateException("NEWSEND_PERIOD_ALREADY_FILED",
                    command.formType().code() + " for EIN " + command.clientEin().masked() + ", period "
                            + command.taxPeriod().begin() + ".." + command.taxPeriod().end()
                            + " was already transmitted or is unresolved. Set allowDuplicatePeriod=true "
                            + "to deliberately file again (e.g. a correction).",
                    blocking);
        }
    }

    private boolean blocksPeriod(SubmissionRecord record) {
        if (record.state() == JournalState.CREATED || record.state() == JournalState.INDETERMINATE) {
            return true;
        }
        if (record.state() != JournalState.TRANSMITTED) {
            return false;
        }
        return ackStore.find(record.submissionId().value())
                .map(ack -> !"Rejected".equals(ack.ackType()))
                .orElse(true);
    }

    private boolean completeQuietly(SubmissionId submissionId, FilingOutcome outcome) {
        try {
            journal.complete(submissionId, outcome, clock.instant());
            return true;
        } catch (RuntimeException e) {
            log.error("Journal write FAILED after transmit of {} — the outcome below is NOT persisted; "
                    + "record it manually: {}", submissionId.value(), outcome, e);
            return false;
        }
    }

    private void logOutcome(Filing filing, FilingOutcome outcome, String environment, boolean journalDurable) {
        if (outcome instanceof FilingOutcome.Transmitted transmitted) {
            log.info("TRANSMITTED {} ({} for EIN {}, {}): deposit id {}, receipts {}, durable={}",
                    filing.submissionId().value(), filing.command().formType().code(),
                    filing.command().clientEin().masked(), environment,
                    transmitted.receipt().depositId(), transmitted.receipt().receiptCount(), journalDurable);
        } else if (outcome instanceof FilingOutcome.Rejected rejected) {
            log.warn("REJECTED {} by the IRS: {} ({})", filing.submissionId().value(),
                    rejected.fault().message(), rejected.fault().logHint());
        } else {
            FilingOutcome.Indeterminate indeterminate = (FilingOutcome.Indeterminate) outcome;
            log.error("INDETERMINATE {} — the IRS MAY hold this filing. Do NOT resubmit. {} ({})",
                    filing.submissionId().value(), indeterminate.fault().message(),
                    indeterminate.fault().logHint());
        }
    }

    /**
     * SHA-256 of the environment, a zero byte, the client id, a zero byte, and the client body.
     * The zero bytes keep two different splits of the same concatenation from sharing a key.
     * The environment lets the same bytes be filed once in ATS and once in PRD.
     */
    static String contentKey(String environment, ClientFiling filing) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(environment.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(filing.clientId().value().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(filing.clientBodyXml().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
