package com.irs.mef.ssnvs;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * SSNVS bulk verification for one tax year's W-2 people, without the Spring context.
 * <pre>
 * build   --csv &lt;export.csv&gt; --out &lt;dir&gt; [--today &lt;yyyy-mm-dd&gt;]   writes &lt;dir&gt;/&lt;ein&gt;/ per employer EIN
 * results --dir &lt;dir&gt;/&lt;ein&gt; --results &lt;ssa-results.txt&gt;           writes fixes.csv in that directory
 * </pre>
 * Output names counts, codes and paths only, never an SSN, a name or a birth date.
 */
public final class SsnvsCli {

    enum Exit {
        CLEAN(0),
        INPUT_ERROR(1),
        ACTION_NEEDED(2);

        final int code;

        Exit(int code) {
            this.code = code;
        }
    }

    private static final String USAGE = String.join(System.lineSeparator(),
            "usage: ssnvs.sh build --csv <export.csv> --out <dir> [--today <yyyy-mm-dd>]",
            "       ssnvs.sh results --dir <dir>/<ein> --results <ssa-results.txt>");
    private static final Map<String, Set<String>> OPTIONS = Map.of(
            "build", Set.of("--csv", "--out", "--today"),
            "results", Set.of("--dir", "--results"));

    private SsnvsCli() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err).code);
    }

    static Exit run(String[] args, PrintStream out, PrintStream err) {
        Optional<Map<String, String>> options = options(args);
        if (options.isEmpty()) {
            err.println(USAGE);
            return Exit.INPUT_ERROR;
        }
        try {
            return "build".equals(args[0]) ? build(options.get(), out, err) : results(options.get(), out, err);
        } catch (SsnvsInputException e) {
            err.println("input error: " + e.getMessage());
            return Exit.INPUT_ERROR;
        } catch (IOException | UncheckedIOException e) {
            err.println("file error: " + e.getClass().getSimpleName() + " " + e.getMessage());
            return Exit.INPUT_ERROR;
        }
    }

    private static Exit build(Map<String, String> options, PrintStream out, PrintStream err) throws IOException {
        if (!options.containsKey("--csv") || !options.containsKey("--out")) {
            err.println(USAGE);
            return Exit.INPUT_ERROR;
        }
        Optional<LocalDate> today = today(options.get("--today"));
        if (today.isEmpty()) {
            err.println("--today must be an ISO date, for example 2027-01-15");
            return Exit.INPUT_ERROR;
        }
        List<W2PersonRow> rows = W2PeopleCsv.read(Files.readAllBytes(Path.of(options.get("--csv"))));
        Exit exit = Exit.CLEAN;
        for (EmployerPlan plan : SsnvsPlanner.plan(rows, today.get())) {
            Optional<SsnvsOutput.Written> written = SsnvsOutput.write(Path.of(options.get("--out")), plan, today.get());
            out.println(summary(plan, written));
            if (plan.legalCompanyInactive()) {
                out.println("EIN " + plan.ein() + ": legal company is inactive (note only; a closed company still files W-2s)");
            }
            if (plan.gate().isPresent() || !plan.held().isEmpty()) {
                exit = Exit.ACTION_NEEDED;
            }
        }
        return exit;
    }

    private static String summary(EmployerPlan plan, Optional<SsnvsOutput.Written> written) {
        String head = "EIN " + (plan.ein().isEmpty() ? "(blank)" : plan.ein()) + " TY" + plan.taxYear() + ": ";
        long notes = plan.sent().stream().mapToLong(person -> person.advisories().size()).sum();
        String counts = "rows " + plan.rowCount() + ", sendable " + plan.sent().size() + ", held " + plan.held().size()
                + ", notes " + notes;
        String dir = written.map(w -> " -> " + w.dir()).orElse("");
        if (plan.gate().isPresent()) {
            return head + "REFUSED " + plan.gate().get() + " (" + counts + ")" + dir;
        }
        Path upload = written.flatMap(SsnvsOutput.Written::upload).orElseThrow();
        return head + counts + " -> upload " + upload + " in BSO with client EIN " + plan.ein();
    }

    private static Exit results(Map<String, String> options, PrintStream out, PrintStream err) throws IOException {
        if (!options.containsKey("--dir") || !options.containsKey("--results")) {
            err.println(USAGE);
            return Exit.INPUT_ERROR;
        }
        Path dir = Path.of(options.get("--dir"));
        SsnvsOutput.Built built = SsnvsOutput.read(dir);
        List<SsnvsResult> parsed = SsnvsResultParser.parse(Files.readAllBytes(Path.of(options.get("--results"))));
        FixList fixes = FixList.reconcile(built.sent(), built.manifest(), parsed);
        Path fixesPath = SsnvsOutput.writeFixes(dir, fixes);
        out.println("EIN " + built.manifest().ein() + " TY" + built.manifest().taxYear() + " results: sent "
                + fixes.sent() + ", verified and alive " + fixes.verifiedAlive() + ", rows to fix " + fixes.rows().size()
                + " -> " + fixesPath);
        return fixes.clean() ? Exit.CLEAN : Exit.ACTION_NEEDED;
    }

    private static Optional<LocalDate> today(String text) {
        if (text == null) {
            return Optional.of(LocalDate.now());
        }
        try {
            return Optional.of(LocalDate.parse(text));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** Empty when the command is unknown, an option is unknown or repeated, or an option has no value. */
    private static Optional<Map<String, String>> options(String[] args) {
        if (args.length == 0 || !OPTIONS.containsKey(args[0]) || args.length % 2 == 0) {
            return Optional.empty();
        }
        Map<String, String> options = new HashMap<>();
        for (int i = 1; i < args.length; i += 2) {
            if (!OPTIONS.get(args[0]).contains(args[i]) || options.put(args[i], args[i + 1]) != null) {
                return Optional.empty();
            }
        }
        return Optional.of(options);
    }
}
