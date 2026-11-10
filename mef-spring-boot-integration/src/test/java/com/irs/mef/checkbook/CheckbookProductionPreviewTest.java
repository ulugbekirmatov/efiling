package com.irs.mef.checkbook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.irs.mef.w2sheet.W2EmployeeSheet;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * Offline preview of the first PRODUCTION check: $0.10, mailed, with a PDF attachment, paid from the
 * default bank account. It builds the request exactly as {@link CheckbookClient} would POST it to
 * api.checkbook.io, catches it in a mock server, and prints it. Nothing leaves the machine and no real key
 * is read. {@code ./mef checkbook-preview <check.json>} names the input; the real one lives in the
 * gitignored {@code data/checkbook/} because it holds a person's name and address.
 */
class CheckbookProductionPreviewTest {

    private static final BigDecimal PREVIEW_AMOUNT = new BigDecimal("0.10");
    private static final String PRODUCTION_HOST = "https://api.checkbook.io/v3";
    private static final String SAMPLE_ATTACHMENT = "sample-w2";
    private static final String SAMPLE_STATEMENT = "src/test/resources/w2sheet/jordan-rivera-2026.json";
    private static final String FICTITIOUS_CHECK = """
            {"payee":"Preview Payee","address":{"line1":"1 Preview Way","city":"Lancaster","state":"PA",
             "zip":"17601"},"mailType":"USPS_FIRST_CLASS","description":"Checkbook production preview",
             "attachment":"sample-w2"}""";
    private static final String CHECK_JSON = """
            {"id":"preview","status":"UNPAID","amount":0.10}""";

    private final ObjectMapper json = new ObjectMapper();
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Test
    void previewsTheTenCentMailedProductionCheckWithoutSendingIt() throws IOException {
        PhysicalCheckRequest request = previewRequest();
        AtomicReference<byte[]> wireBody = new AtomicReference<>();
        server.expect(requestTo(PRODUCTION_HOST + "/check/physical"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(captured -> wireBody.set(((MockClientHttpRequest) captured).getBodyAsBytes()))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(CHECK_JSON));

        // Fake keys and a mock transport: "armed" here only lets the request reach the mock server.
        new CheckbookClient(new CheckbookTransport(builder),
                CheckbookProperties.production("preview-pub", "preview-sec", true))
                .sendPhysicalCheck(request, UUID.randomUUID().toString());

        server.verify();
        JsonNode body = json.readTree(wireBody.get());
        assertEquals(0, PREVIEW_AMOUNT.compareTo(body.get("amount").decimalValue()));
        assertFalse(body.has("account"), "a null funding account debits the default bank account");
        assertArrayEquals(request.attachment().content(),
                Base64.getDecoder().decode(body.at("/attachment/content").asText()));
        System.out.println("checkbook PRODUCTION preview (not sent): POST " + PRODUCTION_HOST + "/check/physical");
        System.out.println(summarize(body, request.attachment()));
    }

    @Test
    void theRealProductionClientRefusesToSendWhileUnarmed() throws IOException {
        CheckbookClient unarmed = new CheckbookClient(new CheckbookTransport(builder),
                CheckbookProperties.production("preview-pub", "preview-sec", false));

        assertThrows(IllegalStateException.class,
                () -> unarmed.sendPhysicalCheck(previewRequest(), UUID.randomUUID().toString()));
        server.verify();
    }

    private PhysicalCheckRequest previewRequest() throws IOException {
        String path = System.getProperty("mef.checkbook.check", "");
        JsonNode check = json.readTree(path.isBlank() ? FICTITIOUS_CHECK : Files.readString(Path.of(path)));
        JsonNode address = check.get("address");
        MailingAddress recipient = new MailingAddress(address.get("line1").asText(),
                address.path("line2").isTextual() ? address.get("line2").asText() : null,
                address.get("city").asText(), address.get("state").asText(), address.get("zip").asText());
        return new PhysicalCheckRequest(check.get("payee").asText(), recipient, PREVIEW_AMOUNT,
                check.get("description").asText(), null, MailType.valueOf(check.get("mailType").asText()),
                attachment(check.get("attachment").asText()));
    }

    private static CheckAttachment attachment(String source) throws IOException {
        if (!SAMPLE_ATTACHMENT.equals(source)) {
            Path file = Path.of(source);
            return new CheckAttachment(file.getFileName().toString(), Files.readAllBytes(file));
        }
        byte[] pdf = W2EmployeeSheet.render(W2EmployeeSheet.parse(Files.readString(Path.of(SAMPLE_STATEMENT))));
        return new CheckAttachment("w2-jordan-rivera-2026.pdf", pdf);
    }

    private static String summarize(JsonNode body, CheckAttachment attachment) {
        ObjectNode shown = body.deepCopy();
        ((ObjectNode) shown.get("attachment")).put("content",
                "<" + attachment.content().length + " bytes, sha256 " + sha256(attachment.content()) + ">");
        return shown.toPrettyString();
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 is always present in the JDK", missing);
        }
    }
}
