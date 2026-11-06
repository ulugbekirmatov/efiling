package com.irs.mef.checkbook;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

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
        return new CheckbookClient(builder, new CheckbookProperties(publishableKey, secretKey));
    }

    private static DigitalCheckRequest oneDollar() {
        return new DigitalCheckRequest("Pyramos Sandbox Probe", "r@example.com", new BigDecimal("1.00"), null);
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
                () -> new DigitalCheckRequest("n", "r@example.com", BigDecimal.ZERO, null));
        assertThrows(IllegalArgumentException.class,
                () -> new DigitalCheckRequest("n", "r@example.com", new BigDecimal("1.001"), null));
    }

    @Test
    void toStringNeverPrintsTheSecret() {
        assertTrue(!new CheckbookProperties("pub", "s3cret").toString().contains("s3cret"));
    }
}
