package com.irs.mef.ssnvs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The SQL the operator runs and the CSV reader are two halves of one contract; this keeps them from drifting. */
class SqlCsvContractTest {

    private static final Path SQL = Path.of("sql", "ssnvs_w2_people.sql");
    private static final Pattern SELECT = Pattern.compile("(?i)\\bSELECT\\b");
    private static final Pattern FROM = Pattern.compile("(?i)^FROM\\b");
    private static final Pattern ALIAS = Pattern.compile("(?is).*\\bAS\\s+([a-z_][a-z0-9_]*)\\s*$");

    @Test
    @DisplayName("the output aliases of sql/ssnvs_w2_people.sql are exactly the CSV contract columns, in order")
    void aliasesMatchContract() throws IOException {
        assertTrue(Files.isRegularFile(SQL), SQL + " is missing");

        assertEquals(W2PeopleCsv.CONTRACT_COLUMNS, outputAliases(Files.readString(SQL)));
    }

    @Test
    @DisplayName("the alias parser reads plain columns, AS aliases and expressions with commas inside parentheses")
    void aliasParser() {
        String sql = "SELECT x.*\n/*!*/FROM (\nWITH w AS (SELECT a FROM t)\nSELECT\n  g.one,\n"
                + "  COALESCE(g.a, g.b) AS two,\n  CASE WHEN g.c = 1 THEN 'x,y' END AS three\nFROM g\n) x";

        assertEquals(List.of("one", "two", "three"), outputAliases(sql));
    }

    /** The select list of the last SELECT in the file: the query x.* wraps. */
    private static List<String> outputAliases(String sql) {
        Matcher select = SELECT.matcher(sql);
        int start = -1;
        while (select.find()) {
            start = select.end();
        }
        List<String> items = new ArrayList<>();
        StringBuilder item = new StringBuilder();
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'') {
                inString = !inString;
            }
            if (!inString && depth == 0 && (c == ',' || FROM.matcher(sql.substring(i)).lookingAt()
                    && Character.isWhitespace(sql.charAt(i - 1)))) {
                items.add(item.toString().trim());
                item.setLength(0);
                if (c != ',') {
                    break;
                }
                continue;
            }
            if (!inString) {
                depth += c == '(' ? 1 : c == ')' ? -1 : 0;
            }
            item.append(c);
        }
        return items.stream().map(SqlCsvContractTest::alias).toList();
    }

    private static String alias(String item) {
        Matcher as = ALIAS.matcher(item);
        String name = as.matches() ? as.group(1) : item.substring(item.lastIndexOf('.') + 1);
        return name.toLowerCase(Locale.ROOT);
    }
}
