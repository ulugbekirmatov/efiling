package com.irs.mef.wagefile;

import java.util.List;

public interface WageFileStore {

    WageFileRun create(int taxYear, List<String> employerEins, byte[] bytes, String priorRunId);

    WageFileRun read(String id);

    byte[] readBytes(String id);

    WageFileRun recordReceipt(String id, String wageFileIdentifier);
}
