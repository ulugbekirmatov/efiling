package com.irs.mef.filing;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Offline. The client body is bounded before any XML parse. */
class ClientFilingTest {

    @Test
    void parseRejectsAnOverLimitBodyBeforeParsingXml() {
        String xml = "<not-xml>" + "x".repeat(ClientFiling.MAX_CLIENT_BODY_CHARS);
        FilingValidationException ex = assertThrows(FilingValidationException.class,
                () -> ClientFiling.parse("orchid", xml));
        assertEquals("clientBody", ex.field());
        assertTrue(ex.getMessage().contains("exceeds"));
    }

    @Test
    void httpLimitsMatchTheClientBodyCap() throws Exception {
        String yml = Files.readString(Path.of("src/main/resources/application.yml"));
        String cap = Integer.toString(ClientFiling.MAX_CLIENT_BODY_CHARS);
        assertTrue(yml.contains("max-http-form-post-size: " + cap), yml);
        assertTrue(yml.contains("max-request-size: " + cap), yml);
    }
}
