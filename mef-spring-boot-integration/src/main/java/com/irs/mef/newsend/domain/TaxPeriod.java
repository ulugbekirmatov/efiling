package com.irs.mef.newsend.domain;

import com.irs.mef.newsend.error.NewSendValidationException;

import java.time.LocalDate;

/** Inclusive tax period. begin &lt;= end is a type invariant, not a runtime check somewhere downstream. */
public record TaxPeriod(LocalDate begin, LocalDate end) {

    public TaxPeriod {
        if (begin == null || end == null) {
            throw new NewSendValidationException("taxPeriod", "taxPeriodBegin and taxPeriodEnd are both required");
        }
        if (end.isBefore(begin)) {
            throw new NewSendValidationException("taxPeriod",
                    "taxPeriodEnd (" + end + ") precedes taxPeriodBegin (" + begin + ")");
        }
    }
}
