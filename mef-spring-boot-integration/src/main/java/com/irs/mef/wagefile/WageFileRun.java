package com.irs.mef.wagefile;

import java.nio.file.Path;
import java.util.List;

/** One stored wage-file run. The bytes stay in {@code bytesPath}; this record is the directory's manifest. */
public record WageFileRun(
        String id,
        int taxYear,
        List<String> employerEins,
        WageFileStatus status,
        String sha256,
        Path bytesPath,
        String wageFileIdentifier,
        String priorRunId) {
}
