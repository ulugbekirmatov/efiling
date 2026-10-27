package com.irs.mef.filing;


import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.Objects;
import java.util.Set;

/**
 * The period a 94x ReturnHeader names: QuarterEndingDt for a quarterly return, TaxYr for an
 * annual one. ReturnHeader94x.xsd makes them an exclusive choice.
 */
sealed interface ReportingPeriod permits ReportingPeriod.Quarter, ReportingPeriod.TaxYear {

    /** Throws when the client's tax period does not cover exactly this reporting period. */
    void requireMatches(TaxPeriod taxPeriod);

    String headerElement();

    String headerValue();

    record Quarter(YearMonth ending) implements ReportingPeriod {

        private static final Set<Integer> QUARTER_MONTHS = Set.of(3, 6, 9, 12);

        public Quarter {
            Objects.requireNonNull(ending, "ending");
            if (!QUARTER_MONTHS.contains(ending.getMonthValue())) {
                throw new ReportingAgentValidationException("quarterEndingDt",
                        "quarterEndingDt month must be 03, 06, 09, or 12");
            }
        }

        @Override
        public void requireMatches(TaxPeriod taxPeriod) {
            YearMonth periodEnd = YearMonth.from(taxPeriod.end());
            if (!periodEnd.equals(ending)) {
                throw new ReportingAgentValidationException("quarterEndingDt",
                        "quarterEndingDt " + ending + " must equal tax period end year-month " + periodEnd);
            }
        }

        @Override
        public String headerElement() {
            return "QuarterEndingDt";
        }

        @Override
        public String headerValue() {
            return ending.toString();
        }
    }

    record TaxYear(Year year) implements ReportingPeriod {

        public TaxYear {
            Objects.requireNonNull(year, "year");
        }

        @Override
        public void requireMatches(TaxPeriod taxPeriod) {
            LocalDate firstDay = year.atDay(1);
            LocalDate lastDay = year.atMonth(12).atEndOfMonth();
            if (!taxPeriod.begin().equals(firstDay) || !taxPeriod.end().equals(lastDay)) {
                throw new ReportingAgentValidationException("taxYr",
                        "taxYr " + year + " requires tax period " + firstDay + ".." + lastDay
                                + ", found " + taxPeriod.begin() + ".." + taxPeriod.end());
            }
        }

        @Override
        public String headerElement() {
            return "TaxYr";
        }

        @Override
        public String headerValue() {
            return year.toString();
        }
    }
}
