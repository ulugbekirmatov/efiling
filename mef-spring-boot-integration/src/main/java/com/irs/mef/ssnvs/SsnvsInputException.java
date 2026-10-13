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
        MIXED_EMPLOYERS,
        BUILD_NOT_RECONCILED,
        BAD_REQUEST_FILE,
        BAD_MANIFEST,
        MANIFEST_MISMATCH,
        BAD_RESULT_RECORD
    }

    private final Problem problem;
    private final int line;
    private final String column;

    public SsnvsInputException(Problem problem, int line, String column) {
        super(message(problem, line, column));
        this.problem = problem;
        this.line = line;
        this.column = column;
    }

    private static String message(Problem problem, int line, String column) {
        String text = problem + " at line " + line + (column == null ? "" : " column " + column);
        if (problem == Problem.BUILD_NOT_RECONCILED) {
            return text + ": run results first or pass --replace";
        }
        return text;
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
