package com.irs.mef.returnpdf;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ReturnPdfVerbTest {

    @Test
    void writesThePaperCopy() throws Exception {
        String in = System.getProperty("mef.pdf.in");
        String out = System.getProperty("mef.pdf.out");
        assumeTrue(in != null && out != null, "set -Dmef.pdf.in=<return.xml> -Dmef.pdf.out=<file.pdf>");

        Files.write(Path.of(out), ReturnPdf.render(Files.readString(Path.of(in))));
    }
}
