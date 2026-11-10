package com.irs.mef.checkbook;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CheckbookClientTest {

    private static final String SANDBOX = "https://api.sandbox.checkbook.io/v3";
    private static final String CHECK_JSON = """
            {"id":"abc123","status":"UNPAID","amount":1.00,"name":"Pyramos Sandbox Probe",
             "recipient":"r@example.com","number":1001,"date":"2026-11-06","unexpected":"ignored"}""";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private CheckbookClient client(String publishableKey, String secretKey) {
        return new CheckbookClient(new CheckbookTransport(builder), CheckbookProperties.sandbox(publishableKey, secretKey));
    }

    private static DigitalCheckRequest oneDollar() {
        return new DigitalCheckRequest("Pyramos Sandbox Probe", "r@example.com", new BigDecimal("1.00"), null, null);
    }

    @Test
    void sendsAuthorizationIdempotencyKeyAndBodyToTheSandbox() {
        server.expect(requestTo(SANDBOX + "/check/digital"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "pub:sec"))
                .andExpect(header("Idempotency-Key", "key-1"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("Pyramos Sandbox Probe"))
                .andExpect(jsonPath("$.recipient").value("r@example.com"))
                .andExpect(jsonPath("$.amount").value(1.00))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(CHECK_JSON));

        CheckbookCheck check = client("pub", "sec").sendDigitalCheck(oneDollar(), "key-1");

        assertEquals("abc123", check.id());
        assertEquals("UNPAID", check.status());
        assertEquals(0, new BigDecimal("1.00").compareTo(check.amount()));
        server.verify();
    }

    @Test
    void mapsAnErrorBodyToACheckbookException() {
        server.expect(requestTo(SANDBOX + "/check/digital"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"Invalid request\",\"more_info\":{\"recipient\":\"must be an email\"}}"));

        CheckbookException thrown = assertThrows(CheckbookException.class,
                () -> client("pub", "sec").sendDigitalCheck(oneDollar(), "key-2"));

        assertEquals(400, thrown.httpStatus());
        assertEquals("Invalid request", thrown.error());
        assertEquals("must be an email", thrown.moreInfo().get("recipient"));
    }

    @Test
    void getsOneCheckAndListsChecks() {
        server.expect(requestTo(SANDBOX + "/check/abc123"))
                .andExpect(header("Authorization", "pub:sec"))
                .andRespond(withSuccess(CHECK_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(SANDBOX + "/check"))
                .andRespond(withSuccess("{\"checks\":[" + CHECK_JSON + "],\"page\":1,\"pages\":1,\"total\":1}",
                        MediaType.APPLICATION_JSON));
        CheckbookClient client = client("pub", "sec");

        CheckbookCheck one = client.getCheck("abc123");
        List<CheckbookCheck> all = client.listChecks();

        assertEquals("abc123", one.id());
        assertEquals(List.of("abc123"), all.stream().map(CheckbookCheck::id).toList());
        server.verify();
    }

    @Test
    void blankKeysRefuseBeforeAnyHttpCall() {
        CheckbookClient client = client("", " ");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> client.sendDigitalCheck(oneDollar(), "key-3"));

        assertTrue(thrown.getMessage().contains("MEF_CHECKBOOK_PUBLISHABLE_KEY"));
        assertTrue(thrown.getMessage().contains("MEF_CHECKBOOK_SECRET_KEY"));
        server.verify();
    }

    @Test
    void requestRejectsAmountsBelowOneCentOrFinerThanACent() {
        assertThrows(IllegalArgumentException.class,
                () -> new DigitalCheckRequest("n", "r@example.com", BigDecimal.ZERO, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new DigitalCheckRequest("n", "r@example.com", new BigDecimal("1.001"), null, null));
    }

    @Test
    void sendsTheAttachmentFilenameAndBase64Content() {
        byte[] pdf = {'%', 'P', 'D', 'F', 0, 1, 2, (byte) 0xFF};
        DigitalCheckRequest request = new DigitalCheckRequest("Pyramos Sandbox Probe", "r@example.com",
                new BigDecimal("1.00"), null, new CheckAttachment("w2.pdf", pdf));
        server.expect(requestTo(SANDBOX + "/check/digital"))
                .andExpect(jsonPath("$.attachment.filename").value("w2.pdf"))
                .andExpect(jsonPath("$.attachment.content").value(Base64.getEncoder().encodeToString(pdf)))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(CHECK_JSON));

        client("pub", "sec").sendDigitalCheck(request, "key-4");

        server.verify();
    }

    @Test
    void omitsTheAttachmentKeyWhenThereIsNone() {
        server.expect(requestTo(SANDBOX + "/check/digital"))
                .andExpect(jsonPath("$.attachment").doesNotExist())
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(CHECK_JSON));

        client("pub", "sec").sendDigitalCheck(oneDollar(), "key-5");

        server.verify();
    }

    @Test
    void getsTheAttachmentBytes() {
        byte[] pdf = {'%', 'P', 'D', 'F', 7};
        server.expect(requestTo(SANDBOX + "/check/abc123/attachment"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "pub:sec"))
                .andRespond(withSuccess(pdf, MediaType.APPLICATION_PDF));

        byte[] received = client("pub", "sec").getAttachment("abc123");

        assertArrayEquals(pdf, received);
        server.verify();
    }

    @Test
    void attachmentRejectsBlankOrTooLongFilenameAndEmptyContent() {
        byte[] content = {1};
        assertThrows(IllegalArgumentException.class, () -> new CheckAttachment(" ", content));
        assertThrows(IllegalArgumentException.class,
                () -> new CheckAttachment("x".repeat(CheckAttachment.MAX_FILENAME_LENGTH + 1), content));
        assertThrows(IllegalArgumentException.class, () -> new CheckAttachment("a.pdf", new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new CheckAttachment("a.pdf", null));
    }

    private static final String MAILED_CHECK_JSON = """
            {"id":"mail1","status":"IN_PROCESS","amount":1.00,"name":"Pyramos Sandbox Probe",
             "recipient":{"city":"Scranton","country":"US","line_1":"1725 Slough Avenue","line_2":null,
             "state":"PA","zip":"18505"},"number":1002,"date":"2026-11-09","image_uri":"https://example.com/c.png"}""";

    private static MailingAddress scranton() {
        return new MailingAddress("1725 Slough Avenue", null, "Scranton", "PA", "18505");
    }

    private static PhysicalCheckRequest mailed(String fundingAccountId, MailType mailType, CheckAttachment attachment) {
        return new PhysicalCheckRequest("Pyramos Sandbox Probe", scranton(), new BigDecimal("1.00"), null,
                fundingAccountId, mailType, attachment);
    }

    @Test
    void sendsAPhysicalCheckWithCheckbookFieldNames() {
        MailingAddress address = new MailingAddress("1725 Slough Avenue", "Suite 4", "Scranton", "PA", "18505");
        PhysicalCheckRequest request = new PhysicalCheckRequest("Pyramos Sandbox Probe", address,
                new BigDecimal("1.00"), "W-2 copy", "wallet-1", MailType.USPS_FIRST_CLASS, null);
        server.expect(requestTo(SANDBOX + "/check/physical"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "pub:sec"))
                .andExpect(header("Idempotency-Key", "key-6"))
                .andExpect(jsonPath("$.recipient.line_1").value("1725 Slough Avenue"))
                .andExpect(jsonPath("$.recipient.line_2").value("Suite 4"))
                .andExpect(jsonPath("$.recipient.city").value("Scranton"))
                .andExpect(jsonPath("$.recipient.state").value("PA"))
                .andExpect(jsonPath("$.recipient.zip").value("18505"))
                .andExpect(jsonPath("$.mail_type").value("USPS_FIRST_CLASS"))
                .andExpect(jsonPath("$.account").value("wallet-1"))
                .andExpect(jsonPath("$.amount").value(1.00))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body(MAILED_CHECK_JSON));

        CheckbookCheck check = client("pub", "sec").sendPhysicalCheck(request, "key-6");

        assertEquals("mail1", check.id());
        server.verify();
    }

    @Test
    void physicalCheckOmitsNullFields() {
        server.expect(requestTo(SANDBOX + "/check/physical"))
                .andExpect(jsonPath("$.recipient.line_2").doesNotExist())
                .andExpect(jsonPath("$.account").doesNotExist())
                .andExpect(jsonPath("$.mail_type").doesNotExist())
                .andExpect(jsonPath("$.attachment").doesNotExist())
                .andExpect(jsonPath("$.description").doesNotExist())
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body(MAILED_CHECK_JSON));

        client("pub", "sec").sendPhysicalCheck(mailed(null, null, null), "key-7");

        server.verify();
    }

    @Test
    void physicalCheckSendsTheAttachmentAsBase64() {
        byte[] pdf = {'%', 'P', 'D', 'F', 0, 1, (byte) 0xFF};
        server.expect(requestTo(SANDBOX + "/check/physical"))
                .andExpect(jsonPath("$.attachment.filename").value("w2.pdf"))
                .andExpect(jsonPath("$.attachment.content").value(Base64.getEncoder().encodeToString(pdf)))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body(MAILED_CHECK_JSON));

        client("pub", "sec").sendPhysicalCheck(mailed(null, null, new CheckAttachment("w2.pdf", pdf)), "key-8");

        server.verify();
    }

    @Test
    void parsesAnAddressObjectRecipientFromGetAndList() {
        server.expect(requestTo(SANDBOX + "/check/mail1")).andRespond(withSuccess(MAILED_CHECK_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(SANDBOX + "/check"))
                .andRespond(withSuccess("{\"checks\":[" + MAILED_CHECK_JSON + "," + CHECK_JSON + "]}",
                        MediaType.APPLICATION_JSON));
        CheckbookClient client = client("pub", "sec");

        CheckbookCheck one = client.getCheck("mail1");
        List<CheckbookCheck> all = client.listChecks();

        assertEquals("1725 Slough Avenue", one.recipient().path("line_1").asText());
        assertEquals(2, all.size());
        assertEquals("1725 Slough Avenue", all.get(0).recipient().path("line_1").asText());
        assertEquals("r@example.com", all.get(1).recipient().asText());
        server.verify();
    }

    @Test
    void createsAWalletAndIgnoresItsAccountNumbers() {
        server.expect(requestTo(SANDBOX + "/account/wallet"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "pub:sec"))
                .andExpect(jsonPath("$.name").value("Pyramos Prefund Sandbox"))
                .andExpect(jsonPath("$.type").value("INTERNAL"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":\"w1\",\"date\":\"2026-11-09\",\"name\":\"Pyramos Prefund Sandbox\","
                                + "\"numbers\":{\"ACH\":{\"account\":\"123\",\"routing\":\"456\"}}}"));

        CheckbookWallet wallet = client("pub", "sec").createWallet("Pyramos Prefund Sandbox", WalletType.INTERNAL);

        assertEquals("w1", wallet.id());
        assertEquals("Pyramos Prefund Sandbox", wallet.name());
        assertEquals("2026-11-09", wallet.date());
        assertTrue(!wallet.toString().contains("123"));
        server.verify();
    }

    @Test
    void listsWalletsWithBalances() {
        server.expect(requestTo(SANDBOX + "/account/wallet"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"wallets\":[{\"id\":\"w1\",\"date\":\"2026-11-09\",\"name\":\"A\","
                        + "\"balance\":12.50,\"numbers\":{}}]}", MediaType.APPLICATION_JSON));

        List<CheckbookWallet> wallets = client("pub", "sec").listWallets();

        assertEquals(1, wallets.size());
        assertEquals("w1", wallets.get(0).id());
        assertEquals(0, new BigDecimal("12.50").compareTo(wallets.get(0).balance()));
        server.verify();
    }

    @Test
    void listsNoWalletsWhenTheBodyHasNone() {
        server.expect(requestTo(SANDBOX + "/account/wallet")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertTrue(client("pub", "sec").listWallets().isEmpty());
        server.verify();
    }

    @Test
    void mailingAddressRejectsBlankRequiredFieldsAndOverlongValues() {
        assertThrows(IllegalArgumentException.class, () -> new MailingAddress(" ", null, "Scranton", "PA", "18505"));
        assertThrows(IllegalArgumentException.class, () -> new MailingAddress("1 Main", null, null, "PA", "18505"));
        assertThrows(IllegalArgumentException.class,
                () -> new MailingAddress("1 Main", null, "Scranton", "PA", "1".repeat(MailingAddress.MAX_ZIP_LENGTH + 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new MailingAddress("1 Main", "x".repeat(MailingAddress.MAX_LINE_LENGTH + 1), "Scranton", "PA", "18505"));
    }

    @Test
    void physicalRequestRejectsBadAmountsNamesAndOversizedAttachments() {
        MailingAddress address = scranton();
        byte[] tooBig = new byte[PhysicalCheckRequest.MAX_ATTACHMENT_BYTES + 1];
        assertThrows(IllegalArgumentException.class,
                () -> new PhysicalCheckRequest("Pyramos", address, BigDecimal.ZERO, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new PhysicalCheckRequest("Pyramos", address, new BigDecimal("1.001"), null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new PhysicalCheckRequest("P", address, BigDecimal.ONE, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> mailed(null, null, new CheckAttachment("w2.pdf", tooBig)));
    }

    @Test
    void toStringNeverPrintsTheSecret() {
        assertTrue(!CheckbookProperties.sandbox("pub", "s3cret").toString().contains("s3cret"));
    }

    @Test
    void unarmedProductionRefusesEveryPostBeforeAnyRequest() {
        CheckbookClient unarmed = new CheckbookClient(new CheckbookTransport(builder),
                CheckbookProperties.production("prod-pub", "prod-sec", false));

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> unarmed.sendDigitalCheck(oneDollar(), "key-prod"));
        assertThrows(IllegalStateException.class, () -> unarmed.createWallet("w", WalletType.INTERNAL));

        assertTrue(refused.getMessage().contains("MEF_CHECKBOOK_PRODUCTION_ARMED"));
        server.verify();
    }

    @Test
    void armedProductionPostsToTheProductionHostWithTheProductionKeys() {
        server.expect(requestTo("https://api.checkbook.io/v3/check/digital"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "prod-pub:prod-sec"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(CHECK_JSON));

        CheckbookClient armed = new CheckbookClient(new CheckbookTransport(builder),
                CheckbookProperties.production("prod-pub", "prod-sec", true));
        armed.sendDigitalCheck(oneDollar(), "key-prod");

        server.verify();
    }

    @Test
    void productionWithoutKeysNamesTheProductionVariables() {
        CheckbookClient keyless = new CheckbookClient(new CheckbookTransport(builder),
                CheckbookProperties.production(null, null, true));

        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> keyless.getCheck("x"));

        assertTrue(refused.getMessage().contains("MEF_CHECKBOOK_PROD_SECRET_KEY"));
    }

    @Test
    void productionToStringMasksTheProductionSecret() {
        assertTrue(!CheckbookProperties.production("pub", "s3cret", false).toString().contains("s3cret"));
    }
}
