package com.irs.mef.inspector.capture;

import com.irs.mef.inspector.ring.MimeRequest;
import com.irs.mef.inspector.ring.SendSnapshot;
import com.irs.mef.inspector.ring.SendSnapshotRing;
import com.irs.mef.inspector.ring.SnapshotId;
import com.irs.mef.inspector.ring.SoapCapture;
import com.irs.mef.inspector.ring.CapturedXml;
import com.irs.mef.newsend.domain.NewSendFiling;
import com.irs.mef.newsend.domain.NewSendManifestXml;
import com.irs.mef.newsend.gateway.SendSubmissionsWireTap;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Thread-confined. The NewSend session lock already serialises transmits, so one
 * session at a time is the production case; the type still must not use static
 * mutable SOAP buffers (per-actor session, merge at commit).
 *
 * close() builds SendSnapshot then ring.commit. Exceptions from disk are logged, not thrown.
 */
@Slf4j
final class CaptureSession implements SendSubmissionsWireTap.Session {

    private final NewSendFiling filing;
    private final SendSnapshotRing ring;
    private final ReflectiveMimeTap mimeTap;
    private final Clock clock;
    private final String environment;
    private final long maxXmlBytes;

    private MimeRequest outbound;
    private SoapCapture inbound = new SoapCapture.Missing("no inbound SOAP");
    private boolean closed;

    CaptureSession(NewSendFiling filing,
                   SendSnapshotRing ring,
                   ReflectiveMimeTap mimeTap,
                   Clock clock,
                   String environment,
                   long maxXmlBytes) {
        this.filing = Objects.requireNonNull(filing);
        this.ring = Objects.requireNonNull(ring);
        this.mimeTap = Objects.requireNonNull(mimeTap);
        this.clock = Objects.requireNonNull(clock);
        this.environment = environment == null ? "" : environment;
        this.maxXmlBytes = maxXmlBytes;
    }

    @Override
    public void attach(Object sendSubmissionsClient) {
        try {
            mimeTap.install(sendSubmissionsClient, this);
        } catch (Exception e) {
            log.warn("inspector: handler attach failed for {}: {}",
                    filing.submissionId().value(), e.toString());
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            persist();
        } catch (Exception e) {
            log.warn("inspector capture failed for {}: {}", filing.submissionId().value(), e.toString());
        } finally {
            mimeTap.release(this);
        }
    }

    void recordOutbound(MimeRequest request) {
        if (request != null) {
            this.outbound = request;
        }
    }

    void recordInbound(SoapCapture response) {
        if (response != null) {
            this.inbound = response;
        }
    }

    MimeRequest takeRequest() {
        return outbound != null
                ? outbound
                : new MimeRequest("", new SoapCapture.Missing("handler not attached"), List.of());
    }

    SoapCapture takeResponse() {
        return inbound;
    }

    private void persist() {
        String returnXml = filing.command().returnXml();
        long returnBytes = returnXml.getBytes(StandardCharsets.UTF_8).length;
        if (returnBytes > maxXmlBytes) {
            log.warn("inspector: skip snapshot {}, return XML exceeds max-xml-bytes",
                    filing.submissionId().value());
            return;
        }
        String manifest = NewSendManifestXml.render(filing);
        if (manifest.getBytes(StandardCharsets.UTF_8).length > maxXmlBytes) {
            log.warn("inspector: skip snapshot {}, manifest exceeds max-xml-bytes",
                    filing.submissionId().value());
            return;
        }
        MimeRequest mime = takeRequest();
        SoapCapture response = takeResponse();
        SendSnapshot snapshot;
        try {
            snapshot = build(mime, response, returnXml, manifest);
        } catch (IllegalArgumentException e) {
            log.warn("inspector: snapshot rejected ({}), storing Return XML with SOAP Missing", e.getMessage());
            snapshot = build(
                    new MimeRequest(mime.contentType(), new SoapCapture.Missing("redaction failed"), mime.attachments()),
                    new SoapCapture.Missing("redaction failed"),
                    returnXml,
                    manifest);
        }
        ring.commit(snapshot);
    }

    private SendSnapshot build(MimeRequest mime, SoapCapture response, String returnXml, String manifest) {
        CapturedXml capturedReturn = new CapturedXml(returnXml);
        return new SendSnapshot(
                new SnapshotId(filing.submissionId().value()),
                clock.instant(),
                environment,
                filing.command().clientEin().masked(),
                filing.command().formType(),
                filing.command().taxPeriod(),
                filing.command().clientRequestId(),
                filing.command().returnXmlSha256(),
                capturedReturn,
                new CapturedXml(manifest),
                mime,
                response);
    }
}
