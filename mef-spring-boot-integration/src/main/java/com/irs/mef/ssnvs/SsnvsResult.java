package com.irs.mef.ssnvs;

/**
 * One parsed SSA result line. Holds no readable name: the echoed name and birth date are kept inside
 * {@link Echo} only so reconciliation can compare them with what was sent. {@code control} is the raw
 * 14-character user control data, which this package wrote.
 */
record SsnvsResult(int line, String control, String ssnLastFour, Echo echo, Verification verification,
                   Death death, String confirmation) {

    /** Result positions 10-47 (last, first, middle, birth date), which echo request positions 16-53. */
    static final class Echo {
        private final String fields;

        Echo(String fields) {
            this.fields = fields;
        }

        boolean matches(SsnvsRequest request) {
            String record = request.record();
            return fields.equals(record.substring(SsnvsRequest.Field.LAST_NAME.start - 1,
                    SsnvsRequest.Field.BIRTH_DATE.end));
        }

        @Override
        public String toString() {
            return "Echo[redacted]";
        }
    }

    boolean echoes(SsnvsRequest request) {
        return echo.matches(request) && ssnLastFour.equals(request.ssn().lastFour());
    }

    @Override
    public String toString() {
        return "SsnvsResult[line=" + line + ", control=" + control + ", verification=" + verification
                + ", death=" + death + "]";
    }
}
