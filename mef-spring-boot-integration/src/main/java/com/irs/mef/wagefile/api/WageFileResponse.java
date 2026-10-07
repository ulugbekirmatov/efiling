package com.irs.mef.wagefile.api;

import com.irs.mef.wagefile.WageFileRun;

public record WageFileResponse(String id, String path, String sha256, String status) {

    public static WageFileResponse from(WageFileRun run) {
        return new WageFileResponse(run.id(), run.bytesPath().toString(), run.sha256(), run.status().name());
    }
}
