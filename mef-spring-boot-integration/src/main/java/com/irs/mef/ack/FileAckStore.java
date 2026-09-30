package com.irs.mef.ack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.irs.mef.newsend.domain.NewSendSubmissionId;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/** Acks carry EIN and amounts: never log file contents. */
@Slf4j
public final class FileAckStore implements AckStore {

    private static final String SUFFIX = ".json";
    private static final String TEMP_SUFFIX = ".json.tmp";

    private final Path root;
    private final ObjectMapper mapper;

    public FileAckStore(Path root, ObjectMapper mapper) {
        this.root = root;
        this.mapper = mapper.copy()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create ack store at " + root, e);
        }
    }

    @Override
    public synchronized void record(StoredAck ack) {
        String submissionId = new NewSendSubmissionId(ack.submissionId()).value();
        Path temp = root.resolve(submissionId + TEMP_SUFFIX);
        Path dest = root.resolve(submissionId + SUFFIX);
        try {
            writeSynced(temp, mapper.writeValueAsBytes(ack));
            Files.move(temp, dest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new IllegalStateException("ack store write failed for " + submissionId, e);
        }
    }

    @Override
    public Optional<StoredAck> find(String submissionId) {
        if (!isSubmissionId(submissionId)) {
            return Optional.empty();
        }
        Path file = root.resolve(submissionId + SUFFIX);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(file.toFile(), StoredAck.class));
        } catch (IOException e) {
            log.warn("ack store: cannot read {}: {}", file.getFileName(), e.toString());
            return Optional.empty();
        }
    }

    private static boolean isSubmissionId(String raw) {
        try {
            new NewSendSubmissionId(raw);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void writeSynced(Path path, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(path,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            channel.write(ByteBuffer.wrap(bytes));
            channel.force(true);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("ack store: cannot delete temp file {}: {}", path.getFileName(), e.toString());
        }
    }
}
