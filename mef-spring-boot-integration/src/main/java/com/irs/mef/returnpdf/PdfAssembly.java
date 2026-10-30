package com.irs.mef.returnpdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Fills each IRS PDF, flattens it so the copy cannot be edited, and concatenates the forms.
 * Trailing pages that received no value are dropped: the 941-V voucher behind Form 941, and a
 * Schedule R continuation sheet when every client fits on page 1.
 */
final class PdfAssembly {

    private PdfAssembly() {}

    static byte[] assemble(List<FilledForm> forms) {
        try (PDDocument out = new PDDocument()) {
            PDFMergerUtility merger = new PDFMergerUtility();
            for (FilledForm form : forms) {
                try (PDDocument filled = fill(form)) {
                    merger.appendDocument(out, filled);
                }
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            out.save(bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new ReturnPdfException("Could not assemble the return PDF: " + e.getMessage(), e);
        }
    }

    private static PDDocument fill(FilledForm form) throws IOException {
        PDDocument document = Loader.loadPDF(template(form.templateResource()));
        PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
        // IRS PDFs also carry an XFA form; viewers that prefer XFA would show it empty.
        acroForm.setXFA(null);
        int lastFilledPage = 0;
        for (Map.Entry<String, String> value : form.values().entrySet()) {
            PDField field = acroForm.getField(value.getKey());
            if (field == null) {
                throw new ReturnPdfException(form.form() + " has no field " + value.getKey()
                        + " in " + form.templateResource());
            }
            for (PDAnnotationWidget widget : field.getWidgets()) {
                lastFilledPage = Math.max(lastFilledPage, document.getPages().indexOf(widget.getPage()));
            }
            try {
                field.setValue(value.getValue());
            } catch (IllegalArgumentException e) {
                throw new ReturnPdfException(form.form() + " field " + value.getKey() + " rejects '"
                        + value.getValue() + "': " + e.getMessage(), e);
            }
        }
        acroForm.flatten();
        while (document.getNumberOfPages() > lastFilledPage + 1) {
            document.removePage(document.getNumberOfPages() - 1);
        }
        return document;
    }

    private static byte[] template(String resource) throws IOException {
        try (InputStream in = PdfAssembly.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new ReturnPdfException("Missing IRS PDF on the classpath: " + resource);
            }
            return in.readAllBytes();
        }
    }
}
