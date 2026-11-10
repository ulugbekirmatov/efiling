package com.irs.mef.checkbook;

import com.irs.mef.live.LiveSandboxTest;
import com.irs.mef.w2sheet.W2EmployeeSheet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Sends $1.00 mailed checks to the Checkbook sandbox, first from the bank and then from a prefund wallet. */
@SpringBootTest
@LiveSandboxTest
class CheckbookMailedCheckSandboxTest {

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

    private static final String WALLET_NAME = "Pyramos Prefund Sandbox";
    private static final String PAYEE_NAME = "Pyramos Sandbox Probe";
    private static final String W2_FILENAME = "w2-jordan-rivera-2026.pdf";
    private static final BigDecimal ONE_DOLLAR = new BigDecimal("1.00");
    private static final MailingAddress RECIPIENT_ADDRESS =
            new MailingAddress("1725 Slough Avenue", null, "Scranton", "PA", "18505");

    @Autowired
    CheckbookClient client;

    @Test
    void sendsOneDollarMailedCheckFromTheBankThenReadsItBack() throws IOException {
        PhysicalCheckRequest request = mailedCheck(null, "Pyramos sandbox mailed check, bank funded");

        CheckbookCheck sent = client.sendPhysicalCheck(request, UUID.randomUUID().toString());
        System.out.println("checkbook sandbox mailed check id=" + sent.id() + " status=" + sent.status());

        CheckbookCheck fetched = client.getCheck(sent.id());
        assertEquals(sent.id(), fetched.id());
        assertEquals(0, ONE_DOLLAR.compareTo(fetched.amount()));
        assertEquals(RECIPIENT_ADDRESS.line1(), fetched.recipient().path("line_1").asText());
    }

    @Test
    void sendsOneDollarMailedCheckFromThePrefundWallet() throws IOException {
        CheckbookWallet wallet = findOrCreateWallet();
        PhysicalCheckRequest request = mailedCheck(wallet.id(), "Pyramos sandbox mailed check, prefunded");

        CheckbookCheck sent = client.sendPhysicalCheck(request, UUID.randomUUID().toString());

        System.out.println("checkbook sandbox prefunded mailed check id=" + sent.id() + " status=" + sent.status());
        assertNotNull(sent.id());
    }

    private CheckbookWallet findOrCreateWallet() {
        return client.listWallets().stream()
                .filter(wallet -> WALLET_NAME.equals(wallet.name()))
                .findFirst()
                .orElseGet(() -> client.createWallet(WALLET_NAME, WalletType.INTERNAL));
    }

    private static PhysicalCheckRequest mailedCheck(String fundingAccountId, String description) throws IOException {
        byte[] pdf = W2EmployeeSheet.render(W2EmployeeSheet.parse(
                Files.readString(Path.of("src/test/resources/w2sheet/jordan-rivera-2026.json"))));
        return new PhysicalCheckRequest(PAYEE_NAME, RECIPIENT_ADDRESS, ONE_DOLLAR, description, fundingAccountId,
                MailType.USPS_FIRST_CLASS, new CheckAttachment(W2_FILENAME, pdf));
    }
}
