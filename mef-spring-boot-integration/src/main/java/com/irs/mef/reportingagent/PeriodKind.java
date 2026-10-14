package com.irs.mef.reportingagent;

import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/** Which ClientReturnBody attribute carries a return type's reporting period, and how to parse it. */
enum PeriodKind {
    QUARTER("quarterEndingDt") {
        @Override
        ReportingPeriod parse(String raw) {
            try {
                return new ReportingPeriod.Quarter(YearMonth.parse(raw));
            } catch (DateTimeParseException e) {
                throw new ReportingAgentValidationException(attribute(), attribute() + " is not yyyy-MM: " + raw);
            }
        }
    },
    TAX_YEAR("taxYr") {
        @Override
        ReportingPeriod parse(String raw) {
            if (!FOUR_DIGITS.matcher(raw).matches()) {
                throw new ReportingAgentValidationException(attribute(), attribute() + " is not yyyy: " + raw);
            }
            return new ReportingPeriod.TaxYear(Year.of(Integer.parseInt(raw)));
        }
    };

    private static final Pattern FOUR_DIGITS = Pattern.compile("[0-9]{4}");

    private final String attribute;

    PeriodKind(String attribute) {
        this.attribute = attribute;
    }

    String attribute() {
        return attribute;
    }

    abstract ReportingPeriod parse(String raw);
}
