package com.irs.mef.wagefile;

import java.util.List;

/** JSON stored beside the EFW2 bytes. The path is derived from the run id, not from this file. */
public record WageFileManifest(
        String id,
        int taxYear,
        List<String> employerEins,
        WageFileStatus status,
        String sha256,
        String wageFileIdentifier,
        String priorRunId) {
}
