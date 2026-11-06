package com.irs.mef.filing;

import java.util.Objects;

/** One local reject rule against one preflight. */
public record RuleResult(String irsCode, boolean passed, String detail) {

    public RuleResult {
        Objects.requireNonNull(irsCode, "irsCode");
        detail = detail == null ? "" : detail;
    }
}
