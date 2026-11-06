package com.irs.mef.checkbook;

import com.irs.mef.live.LiveSandboxTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.nio.file.Path;
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

    @Autowired
    CheckbookClient client;

    @Test
    void sendsOneDollarCheckThenReadsItBack() {
        String recipient = System.getProperty("mef.checkbook.recipient", DEFAULT_RECIPIENT);
        DigitalCheckRequest request = new DigitalCheckRequest(
                "Pyramos Sandbox Probe", recipient, new BigDecimal("1.00"), "Pyramos sandbox probe");

        CheckbookCheck sent = client.sendDigitalCheck(request, UUID.randomUUID().toString());
        System.out.println("checkbook sandbox check id=" + sent.id() + " status=" + sent.status());

        CheckbookCheck fetched = client.getCheck(sent.id());
        assertEquals(sent.id(), fetched.id());
        assertEquals(0, new BigDecimal("1.00").compareTo(fetched.amount()));
        assertTrue(client.listChecks().stream().anyMatch(check -> sent.id().equals(check.id())));
    }
}
