package com.irs.mef.inspector.ring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irs.mef.newsend.domain.FormType;
import com.irs.mef.newsend.domain.TaxPeriod;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Layout (all under mef.inspector.path, already gitignored via data/):
 *   {submissionId}/meta.json
 *   {submissionId}/return.xml          original packed bytes
 *   {submissionId}/manifest.xml
 *   {submissionId}/soap-request.xml    redacted SOAP part, absent if Missing
 *   {submissionId}/soap-response.xml   redacted, optional
 *   {submissionId}/mime.json           contentType + omitted attachments
 *
 * Write to {id}.tmp/ then atomic move to {id}/. Delete {id}.tmp on failure.
 * After commit, delete oldest directories until count &lt;= ringSize.
 * Never log file contents.
 */
@Slf4j
public final class FileSendSnapshotRing implements SendSnapshotRing {

    private final Path root;
    private final int ringSize;
    private final ObjectMapper mapper;

    public FileSendSnapshotRing(Path root, int ringSize, ObjectMapper mapper) {
        if (ringSize < 1 || ringSize > 100) {
            throw new IllegalArgumentException("ringSize 1..100");
        }
        this.root = root;
        this.ringSize = ringSize;
        this.mapper = mapper;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create inspector ring at " + root, e);
        }
    }

    @Override
    public List<SnapshotSummary> listNewestFirst() {
        List<SnapshotSummary> summaries = new ArrayList<>();
        for (Path dir : snapshotDirectories()) {
            try {
                MetaFile meta = readMeta(dir);
                if (meta != null) {
                    summaries.add(toSummary(meta));
                }
            } catch (Exception e) {
                log.warn("inspector: skipping unreadable slot {}: {}", dir.getFileName(), e.toString());
            }
        }
        summaries.sort(Comparator.comparing(SnapshotSummary::capturedAt).reversed());
        return List.copyOf(summaries);
    }

    @Override
    public Optional<SendSnapshot> get(SnapshotId id) {
        Path dir = root.resolve(id.value());
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        try {
            return Optional.of(readSnapshot(dir, id));
        } catch (Exception e) {
            log.warn("inspector: cannot read snapshot {}: {}", id.value(), e.toString());
            return Optional.empty();
        }
    }

    @Override
    public synchronized void commit(SendSnapshot snapshot) {
        String id = snapshot.submissionId().value();
        Path tmp = root.resolve(id + ".tmp");
        Path dest = root.resolve(id);
        try {
            deleteRecursively(tmp);
            Files.createDirectories(tmp);
            writeSlot(tmp, snapshot);
            if (Files.exists(dest)) {
                deleteRecursively(dest);
            }
            Files.move(tmp, dest, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            try {
                deleteRecursively(tmp);
            } catch (IOException ignored) {
                // best-effort
            }
            throw new IllegalStateException("inspector ring commit failed for " + id, e);
        }
        evict();
    }

    @Override
    public int size() {
        return snapshotDirectories().size();
    }

    private void evict() {
        List<Path> dirs = snapshotDirectories();
        if (dirs.size() <= ringSize) {
            return;
        }
        List<DirAge> ages = new ArrayList<>();
        for (Path dir : dirs) {
            Instant capturedAt = Instant.EPOCH;
            try {
                MetaFile meta = readMeta(dir);
                if (meta != null && meta.capturedAt() != null) {
                    capturedAt = Instant.parse(meta.capturedAt());
                }
            } catch (Exception e) {
                log.warn("inspector: treating unreadable slot as oldest: {}", dir.getFileName());
            }
            ages.add(new DirAge(dir, capturedAt));
        }
        ages.sort(Comparator.comparing(DirAge::capturedAt));
        int toDelete = ages.size() - ringSize;
        for (int i = 0; i < toDelete; i++) {
            try {
                deleteRecursively(ages.get(i).dir());
            } catch (IOException e) {
                log.warn("inspector eviction failed for {}: {}", ages.get(i).dir().getFileName(), e.toString());
            }
        }
    }

    private void writeSlot(Path dir, SendSnapshot snapshot) throws IOException {
        writeUtf8(dir.resolve("return.xml"), snapshot.returnXml().original());
        writeUtf8(dir.resolve("manifest.xml"), snapshot.manifestXml().original());
        Optional<CapturedXml> soapRequest = snapshot.mimeRequest().soapPart();
        if (soapRequest.isPresent()) {
            writeUtf8(dir.resolve("soap-request.xml"), soapRequest.get().original());
        }
        if (snapshot.soapResponse() instanceof SoapCapture.Present present) {
            writeUtf8(dir.resolve("soap-response.xml"), present.xml().original());
        }
        MimeFile mimeFile = new MimeFile(
                snapshot.mimeRequest().contentType(),
                snapshot.mimeRequest().attachments());
        writeUtf8(dir.resolve("mime.json"), mapper.writeValueAsString(mimeFile));
        MetaFile meta = new MetaFile(
                snapshot.submissionId().value(),
                snapshot.capturedAt().toString(),
                snapshot.environment(),
                snapshot.einMasked(),
                snapshot.formType().code(),
                snapshot.taxPeriod().begin().toString(),
                snapshot.taxPeriod().end().toString(),
                snapshot.clientRequestId(),
                snapshot.returnXmlSha256(),
                soapRequest.isPresent() ? null : missingReason(snapshot.mimeRequest().soapCapture()),
                snapshot.soapResponse() instanceof SoapCapture.Missing missing ? missing.reason() : null);
        writeUtf8(dir.resolve("meta.json"), mapper.writeValueAsString(meta));
    }

    private SendSnapshot readSnapshot(Path dir, SnapshotId id) throws IOException {
        MetaFile meta = readMeta(dir);
        if (meta == null) {
            throw new IOException("missing meta.json");
        }
        String returnXml = Files.readString(dir.resolve("return.xml"), StandardCharsets.UTF_8);
        String manifestXml = Files.readString(dir.resolve("manifest.xml"), StandardCharsets.UTF_8);
        MimeFile mimeFile = mapper.readValue(dir.resolve("mime.json").toFile(), MimeFile.class);
        Path soapRequestPath = dir.resolve("soap-request.xml");
        SoapCapture soapRequest;
        if (Files.exists(soapRequestPath)) {
            soapRequest = new SoapCapture.Present(
                    new CapturedXml(Files.readString(soapRequestPath, StandardCharsets.UTF_8)));
        } else {
            soapRequest = new SoapCapture.Missing(
                    meta.soapRequestMissingReason() != null
                            ? meta.soapRequestMissingReason()
                            : "SOAP part was not captured");
        }
        Path soapResponsePath = dir.resolve("soap-response.xml");
        SoapCapture soapResponse;
        if (Files.exists(soapResponsePath)) {
            soapResponse = new SoapCapture.Present(
                    new CapturedXml(Files.readString(soapResponsePath, StandardCharsets.UTF_8)));
        } else {
            soapResponse = new SoapCapture.Missing(
                    meta.soapResponseMissingReason() != null
                            ? meta.soapResponseMissingReason()
                            : "no inbound SOAP");
        }
        MimeRequest mimeRequest = new MimeRequest(
                mimeFile.contentType(),
                soapRequest,
                mimeFile.attachments() == null ? List.of() : mimeFile.attachments());
        return new SendSnapshot(
                id,
                Instant.parse(meta.capturedAt()),
                meta.environment(),
                meta.einMasked(),
                FormType.parse(meta.formType()),
                new TaxPeriod(LocalDate.parse(meta.taxPeriodBegin()), LocalDate.parse(meta.taxPeriodEnd())),
                meta.clientRequestId(),
                meta.returnXmlSha256(),
                new CapturedXml(returnXml),
                new CapturedXml(manifestXml),
                mimeRequest,
                soapResponse);
    }

    private MetaFile readMeta(Path dir) throws IOException {
        Path meta = dir.resolve("meta.json");
        if (!Files.exists(meta)) {
            return null;
        }
        return mapper.readValue(meta.toFile(), MetaFile.class);
    }

    private SnapshotSummary toSummary(MetaFile meta) {
        return new SnapshotSummary(
                new SnapshotId(meta.submissionId()),
                Instant.parse(meta.capturedAt()),
                meta.einMasked(),
                meta.formType(),
                meta.taxPeriodBegin() + " to " + meta.taxPeriodEnd(),
                meta.soapRequestMissingReason() == null);
    }

    private List<Path> snapshotDirectories() {
        List<Path> dirs = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return dirs;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path path : stream) {
                if (Files.isDirectory(path) && SnapshotId.tryParse(path.getFileName().toString()).isPresent()) {
                    dirs.add(path);
                }
            }
        } catch (IOException e) {
            log.warn("inspector: cannot list ring at {}: {}", root, e.toString());
        }
        return dirs;
    }

    private static String missingReason(SoapCapture soapPart) {
        return soapPart instanceof SoapCapture.Missing missing ? missing.reason() : "SOAP part was not captured";
    }

    private static void writeUtf8(Path path, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(path,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            channel.write(ByteBuffer.wrap(bytes));
            channel.force(true);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw e;
        }
    }

    private record DirAge(Path dir, Instant capturedAt) {
    }

    record MetaFile(
            String submissionId,
            String capturedAt,
            String environment,
            String einMasked,
            String formType,
            String taxPeriodBegin,
            String taxPeriodEnd,
            String clientRequestId,
            String returnXmlSha256,
            String soapRequestMissingReason,
            String soapResponseMissingReason) {
    }

    record MimeFile(String contentType, List<OmittedAttachment> attachments) {
    }
}
