package com.irs.mef.w2sheet;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

class W2SheetVerbTest {

    @Test
    void writesTheEmployeeSheet() throws Exception {
        String in = System.getProperty("mef.w2.in");
        String out = System.getProperty("mef.w2.out");
        assumeTrue(in != null && out != null, "set -Dmef.w2.in=<statement.json> -Dmef.w2.out=<file.pdf>");

        Files.write(Path.of(out), W2EmployeeSheet.render(W2EmployeeSheet.parse(Files.readString(Path.of(in)))));
    }
}
