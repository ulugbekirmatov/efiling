package com.irs.mef.checkbook;

import com.irs.mef.live.LiveSandboxTest;
import com.irs.mef.w2sheet.W2EmployeeSheet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sends one $1.00 digital check to the Checkbook sandbox, reads it back, and finds it in the list. */
@SpringBootTest
@LiveSandboxTest
class CheckbookSandboxTest {

    static {
        String existing = System.getProperty("A2A_TOOLKIT_HOME");
        if (existing == null || existing.isBlank()) {
            System.setProperty(
                    "A2A_TOOLKIT_HOME",
                    Path.of(System.getProperty("user.dir"), "src/main/resources/mef_config")
                            .toAbsolutePath()
                            .toString());
        }
    }

    private static final String DEFAULT_RECIPIENT = "sandbox-recipient@example.com";
    private static final int ATTACHMENT_POLL_ATTEMPTS = 15;
    private static final Duration ATTACHMENT_POLL_INTERVAL = Duration.ofSeconds(2);

    @Autowired
    CheckbookClient client;

    @Test
    void sendsOneDollarCheckThenReadsItBack() {
        String recipient = System.getProperty("mef.checkbook.recipient", DEFAULT_RECIPIENT);
        DigitalCheckRequest request = new DigitalCheckRequest(
                "Pyramos Sandbox Probe", recipient, new BigDecimal("1.00"), "Pyramos sandbox probe", null);

        CheckbookCheck sent = client.sendDigitalCheck(request, UUID.randomUUID().toString());
        System.out.println("checkbook sandbox check id=" + sent.id() + " status=" + sent.status());

        CheckbookCheck fetched = client.getCheck(sent.id());
        assertEquals(sent.id(), fetched.id());
        assertEquals(0, new BigDecimal("1.00").compareTo(fetched.amount()));
        assertTrue(client.listChecks().stream().anyMatch(check -> sent.id().equals(check.id())));
    }

    @Test
    void sendsW2PdfAttachmentThenReadsItBack() throws IOException, InterruptedException {
        byte[] pdf = W2EmployeeSheet.render(W2EmployeeSheet.parse(
                Files.readString(Path.of("src/test/resources/w2sheet/jordan-rivera-2026.json"))));
        String recipient = System.getProperty("mef.checkbook.recipient", DEFAULT_RECIPIENT);
        DigitalCheckRequest request = new DigitalCheckRequest(
                "Pyramos Sandbox Probe", recipient, new BigDecimal("1.00"), "Pyramos sandbox W-2 attachment",
                new CheckAttachment("w2-jordan-rivera-2026.pdf", pdf));

        CheckbookCheck sent = client.sendDigitalCheck(request, UUID.randomUUID().toString());
        System.out.println("checkbook sandbox attachment check id=" + sent.id() + " number=" + sent.number());
        byte[] stored = awaitAttachment(sent.id());

        // Checkbook rewrites the PDF and stamps "<prefix>-<check number>" on every page, so compare words, not bytes.
        String stamp = "-" + sent.number();
        List<String> storedWords = words(stored);
        storedWords.removeIf(word -> word.endsWith(stamp));
        assertEquals(pageCount(pdf), pageCount(stored), "attachment keeps the W-2 page count");
        assertEquals(words(pdf), storedWords, "attachment keeps every W-2 word");
    }

    // The attachment is stored asynchronously; right after the send, GET .../attachment answers 404.
    private byte[] awaitAttachment(String checkId) throws InterruptedException {
        for (int attempt = 1; ; attempt++) {
            try {
                return client.getAttachment(checkId);
            } catch (CheckbookException notYet) {
                if (notYet.httpStatus() != HttpStatus.NOT_FOUND.value() || attempt == ATTACHMENT_POLL_ATTEMPTS) {
                    throw notYet;
                }
                Thread.sleep(ATTACHMENT_POLL_INTERVAL.toMillis());
            }
        }
    }

    private static int pageCount(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
    }

    private static List<String> words(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            List<String> words = new ArrayList<>(List.of(new PDFTextStripper().getText(document).trim().split("\\s+")));
            Collections.sort(words);
            return words;
        }
    }
}
