package com.irs.mef.w2sheet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Stream;

/**
 * The IRS Notice to Employee and Instructions for Employee from the back of fw2.pdf, one file per
 * tax year, written by scripts/extract-w2-instructions.py.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record EmployeeInstructions(List<Paragraph> notice, List<Paragraph> instructions) {

    /** A heading, or a body paragraph whose optional lead ("Box 1.", "D—") prints in bold. */
    record Paragraph(String heading, String lead, String text) {

        boolean isHeading() {
            return heading != null;
        }

        List<TextWrap.Word> words() {
            List<TextWrap.Word> lead = this.lead == null ? List.of() : TextWrap.words(this.lead, Pen.SANS_BOLD);
            return Stream.concat(lead.stream(), TextWrap.words(text, Pen.SANS).stream()).toList();
        }

        int wordCount() {
            return isHeading() ? TextWrap.words(heading, Pen.SANS_BOLD).size() : words().size();
        }
    }

    static EmployeeInstructions forTaxYear(int taxYear) {
        String resource = "w2sheet/" + taxYear + "/employee-instructions.json";
        try (InputStream in = EmployeeInstructions.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new W2SheetException("No W-2 employee instructions for tax year " + taxYear + " (" + resource + ")");
            }
            return new ObjectMapper().readValue(in, EmployeeInstructions.class);
        } catch (IOException e) {
            throw new W2SheetException("Could not read " + resource + ": " + e.getMessage(), e);
        }
    }
}
