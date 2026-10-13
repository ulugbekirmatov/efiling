package com.irs.mef.ssnvs;

/**
 * The only exception this package raises for bad input. It has no free-text parameter: the message is the
 * problem, the line and a column name taken from the CSV contract or a fixed file label, never a value.
 */
public final class SsnvsInputException extends RuntimeException {

    public enum Problem {
        MISSING_COLUMN,
        BAD_CSV_QUOTING,
        BAD_ROW_WIDTH,
        BAD_VALUE,
        NO_ROWS,
        MIXED_TAX_YEARS,
        BAD_REQUEST_FILE,
        BAD_MANIFEST,
        MANIFEST_MISMATCH,
        BAD_RESULT_RECORD
    }

    private final Problem problem;
    private final int line;
    private final String column;

    public SsnvsInputException(Problem problem, int line, String column) {
        super(problem + " at line " + line + (column == null ? "" : " column " + column));
        this.problem = problem;
        this.line = line;
        this.column = column;
    }

    public Problem problem() {
        return problem;
    }

    public int line() {
        return line;
    }

    public String column() {
        return column;
    }
}
