package com.irs.mef.ssnvs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irs.mef.ssnvs.EmployerPlan.HeldPerson;
import com.irs.mef.ssnvs.EmployerPlan.PersonRefs;
import com.irs.mef.ssnvs.EmployerPlan.SentPerson;
import com.irs.mef.ssnvs.SsnvsInputException.Problem;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The per-EIN output directory: {@code <out>/<ein>/}. Only ssnvs.txt (and its zip) carries PII, because
 * SSA requires it; the manifest and the CSVs carry ids, codes and company codes only. A rebuild replaces the
 * whole directory, so a stale request file or fix list never sits next to a new build.
 */
final class SsnvsOutput {

    static final String REQUEST = "ssnvs.txt";
    static final String ZIP = "ssnvs.zip";
    static final String MANIFEST = "manifest.json";
    static final String HOLDBACKS = "holdbacks.csv";
    static final String NOTES = "notes.csv";
    static final String FIXES = "fixes.csv";
    /** SSA wants a PKZip file holding exactly one file when a bulk request has more than 500 records. */
    static final int ZIP_ABOVE_RECORDS = 500;
    static final String CRLF = "\r\n";

    private static final List<String> CODE_COLUMNS = List.of("employee_id", "w2_form_ids", "company_codes", "code");
    private static final ObjectMapper JSON = new ObjectMapper();

    private SsnvsOutput() {
    }

    /** What a build wrote for one EIN. {@code upload} is empty when a file gate refused the request file. */
    record Written(Path dir, Optional<Path> upload) {
    }

    /** What a build left behind, read back for reconciliation. */
    record Built(List<SsnvsRequest> sent, Manifest manifest) {
    }

    /** Writes nothing for EIN_INVALID: there is no directory to name. */
    static Optional<Written> write(Path outRoot, EmployerPlan plan, LocalDate builtOn) throws IOException {
        if (plan.gate().filter(gate -> gate == FileGate.EIN_INVALID).isPresent()) {
            return Optional.empty();
        }
        Files.createDirectories(outRoot);
        Path target = outRoot.resolve(plan.ein());
        Path staging = outRoot.resolve("." + plan.ein() + ".partial");
        deleteTree(staging);
        Files.createDirectories(staging);
        Files.writeString(staging.resolve(HOLDBACKS), holdbacksCsv(plan.held()), StandardCharsets.UTF_8);
        Files.writeString(staging.resolve(NOTES), notesCsv(plan.sent()), StandardCharsets.UTF_8);
        Optional<Path> upload = Optional.empty();
        if (plan.gate().isEmpty()) {
            byte[] request = requestFile(plan.sent());
            Files.write(staging.resolve(REQUEST), request);
            Files.writeString(staging.resolve(MANIFEST),
                    JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Manifest.of(plan, builtOn)),
                    StandardCharsets.UTF_8);
            String uploadName = REQUEST;
            if (plan.sent().size() > ZIP_ABOVE_RECORDS) {
                Files.write(staging.resolve(ZIP), zip(request, builtOn));
                uploadName = ZIP;
            }
            upload = Optional.of(target.resolve(uploadName));
        }
        replace(staging, target);
        return Optional.of(new Written(target, upload));
    }

    static byte[] requestFile(List<SentPerson> sent) {
        return sent.stream().map(person -> person.request().record() + CRLF).collect(Collectors.joining())
                .getBytes(StandardCharsets.US_ASCII);
    }

    static Built read(Path einDir) throws IOException {
        Path requestPath = einDir.resolve(REQUEST);
        if (!Files.isRegularFile(requestPath)) {
            throw new SsnvsInputException(Problem.BAD_REQUEST_FILE, 0, REQUEST);
        }
        String text = Files.readString(requestPath, StandardCharsets.ISO_8859_1);
        List<SsnvsRequest> sent = new ArrayList<>();
        String[] records = text.split(CRLF, -1);
        if (!records[records.length - 1].isEmpty()) {
            throw new SsnvsInputException(Problem.BAD_REQUEST_FILE, records.length, REQUEST);
        }
        for (int i = 0; i < records.length - 1; i++) {
            try {
                sent.add(SsnvsRequest.parse(records[i]));
            } catch (IllegalArgumentException e) {
                throw new SsnvsInputException(Problem.BAD_REQUEST_FILE, i + 1, REQUEST);
            }
        }
        Manifest manifest;
        try {
            manifest = JSON.readValue(einDir.resolve(MANIFEST).toFile(), Manifest.class);
        } catch (IOException e) {
            throw new SsnvsInputException(Problem.BAD_MANIFEST, 0, MANIFEST);
        }
        if (manifest.recordCount() != sent.size()) {
            throw new SsnvsInputException(Problem.MANIFEST_MISMATCH, 0, MANIFEST);
        }
        return new Built(sent, manifest);
    }

    static Path writeFixes(Path einDir, FixList fixes) throws IOException {
        Path path = einDir.resolve(FIXES);
        String csv = csvLine(FixList.COLUMNS) + fixes.rows().stream().map(row -> csvLine(row.cells()))
                .collect(Collectors.joining());
        Files.writeString(path, csv, StandardCharsets.UTF_8);
        return path;
    }

    private static String holdbacksCsv(List<HeldPerson> held) {
        return codesCsv(held.stream().flatMap(person -> person.holdbacks().stream()
                .map(code -> codeRow(person.refs(), code.name()))));
    }

    private static String notesCsv(List<SentPerson> sent) {
        return codesCsv(sent.stream().flatMap(person -> person.advisories().stream()
                .map(code -> codeRow(person.refs(), code.name()))));
    }

    private static List<String> codeRow(PersonRefs refs, String code) {
        return List.of(Long.toString(refs.employeeId()), nullToEmpty(refs.w2FormIds()),
                nullToEmpty(refs.companyCodes()), code);
    }

    private static String codesCsv(Stream<List<String>> rows) {
        return csvLine(CODE_COLUMNS) + rows.map(SsnvsOutput::csvLine).collect(Collectors.joining());
    }

    static String csvLine(List<String> cells) {
        return cells.stream().map(SsnvsOutput::csvCell).collect(Collectors.joining(",")) + CRLF;
    }

    private static String csvCell(String cell) {
        boolean needsQuotes = cell.contains(",") || cell.contains("\"") || cell.contains("\r") || cell.contains("\n");
        return needsQuotes ? "\"" + cell.replace("\"", "\"\"") + "\"" : cell;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** One entry named ssnvs.txt, timestamped with the build date so the same build gives the same bytes. */
    private static byte[] zip(byte[] request, LocalDate builtOn) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            ZipEntry entry = new ZipEntry(REQUEST);
            entry.setTimeLocal(builtOn.atStartOfDay());
            zip.putNextEntry(entry);
            zip.write(request);
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static void replace(Path staging, Path target) throws IOException {
        Path previous = target.resolveSibling("." + target.getFileName() + ".previous");
        deleteTree(previous);
        if (Files.exists(target)) {
            Files.move(target, previous);
        }
        Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
        deleteTree(previous);
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
