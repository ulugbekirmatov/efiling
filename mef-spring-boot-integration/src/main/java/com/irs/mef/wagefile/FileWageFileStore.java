package com.irs.mef.wagefile;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

public final class FileWageFileStore implements WageFileStore {

    private static final String BYTES_NAME = "efw2";
    private static final String MANIFEST_NAME = "run.json";

    private final Path root;
    private final ObjectMapper mapper;

    public FileWageFileStore(Path root, ObjectMapper mapper) {
        this.root = root;
        this.mapper = mapper;
    }

    @Override
    public WageFileRun create(int taxYear, List<String> employerEins, byte[] bytes, String priorRunId) {
        if (bytes == null) {
            throw new IllegalArgumentException("wage file bytes are required");
        }
        String id = UUID.randomUUID().toString();
        Path dir = directory(id);
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(BYTES_NAME), bytes);
            WageFileManifest manifest = new WageFileManifest(
                    id, taxYear, List.copyOf(employerEins), WageFileStatus.BUILT, sha256(bytes), null, priorRunId);
            mapper.writeValue(dir.resolve(MANIFEST_NAME).toFile(), manifest);
            return toRun(manifest);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public WageFileRun read(String id) {
        WageFileManifest manifest = readManifest(id);
        byte[] bytes = readBytes(id);
        String actual = sha256(bytes);
        if (!actual.equals(manifest.sha256())) {
            throw new IllegalStateException("wage file " + id + " bytes do not match sha256");
        }
        return toRun(manifest);
    }

    @Override
    public byte[] readBytes(String id) {
        try {
            return Files.readAllBytes(directory(id).resolve(BYTES_NAME));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public WageFileRun recordReceipt(String id, String wageFileIdentifier) {
        if (wageFileIdentifier == null || wageFileIdentifier.length() != 6) {
            throw new IllegalArgumentException("wageFileIdentifier must be 6 characters");
        }
        WageFileManifest current = readManifest(id);
        WageFileManifest updated = new WageFileManifest(
                current.id(),
                current.taxYear(),
                current.employerEins(),
                WageFileStatus.UPLOADED,
                current.sha256(),
                wageFileIdentifier,
                current.priorRunId());
        try {
            mapper.writeValue(directory(id).resolve(MANIFEST_NAME).toFile(), updated);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return toRun(updated);
    }

    private WageFileManifest readManifest(String id) {
        try {
            return mapper.readValue(directory(id).resolve(MANIFEST_NAME).toFile(), WageFileManifest.class);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private Path directory(String id) {
        if (id == null || !id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new IllegalArgumentException("wage file id is not a run id");
        }
        return root.resolve(id);
    }

    private WageFileRun toRun(WageFileManifest manifest) {
        return new WageFileRun(
                manifest.id(),
                manifest.taxYear(),
                manifest.employerEins(),
                manifest.status(),
                manifest.sha256(),
                directory(manifest.id()).resolve(BYTES_NAME),
                manifest.wageFileIdentifier(),
                manifest.priorRunId());
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
