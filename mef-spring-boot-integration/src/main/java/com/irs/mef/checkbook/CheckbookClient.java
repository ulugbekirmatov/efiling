package com.irs.mef.checkbook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Checkbook.io API client. {@link CheckbookProperties#environment()} picks the host and the key pair. */
@Component
@EnableConfigurationProperties(CheckbookProperties.class)
public class CheckbookClient {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String MISSING_KEYS_MESSAGE =
            "Checkbook keys are not set for %s: export %s";
    private static final String SANDBOX_KEY_VARIABLES = "MEF_CHECKBOOK_PUBLISHABLE_KEY and MEF_CHECKBOOK_SECRET_KEY";
    private static final String PRODUCTION_KEY_VARIABLES =
            "MEF_CHECKBOOK_PROD_PUBLISHABLE_KEY and MEF_CHECKBOOK_PROD_SECRET_KEY";
    private static final String UNARMED_MESSAGE =
            "Checkbook PRODUCTION is not armed: a POST would move real money. Set MEF_CHECKBOOK_PRODUCTION_ARMED=true"
                    + " only for an approved send.";

    private final RestClient http;
    private final CheckbookProperties properties;
    private final ObjectMapper json = new ObjectMapper();

    public CheckbookClient(CheckbookTransport transport, CheckbookProperties properties) {
        this.properties = properties;
        this.http = transport.builder()
                .baseUrl(properties.environment().baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        properties.activePublishableKey() + ":" + properties.activeSecretKey())
                .defaultStatusHandler(status -> !status.is2xxSuccessful(), (req, res) -> {
                    throw toException(res.getStatusCode().value(), res.getBody().readAllBytes());
                })
                .build();
    }

    public CheckbookCheck sendDigitalCheck(DigitalCheckRequest request, String idempotencyKey) {
        requireKeysAndArmed();
        return http.post()
                .uri("/check/digital")
                .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(toJsonBytes(request))
                .retrieve()
                .body(CheckbookCheck.class);
    }

    public CheckbookCheck sendPhysicalCheck(PhysicalCheckRequest request, String idempotencyKey) {
        requireKeysAndArmed();
        return http.post()
                .uri("/check/physical")
                .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(toJsonBytes(request))
                .retrieve()
                .body(CheckbookCheck.class);
    }

    public CheckbookWallet createWallet(String name, WalletType type) {
        requireKeysAndArmed();
        return http.post()
                .uri("/account/wallet")
                .contentType(MediaType.APPLICATION_JSON)
                .body(toJsonBytes(new CreateWalletRequest(name, type)))
                .retrieve()
                .body(CheckbookWallet.class);
    }

    public List<CheckbookWallet> listWallets() {
        requireKeys();
        WalletList list = http.get()
                .uri("/account/wallet")
                .retrieve()
                .body(WalletList.class);
        return list == null || list.wallets() == null ? List.of() : list.wallets();
    }

    public CheckbookCheck getCheck(String id) {
        requireKeys();
        return http.get()
                .uri("/check/{id}", id)
                .retrieve()
                .body(CheckbookCheck.class);
    }

    public byte[] getAttachment(String checkId) {
        requireKeys();
        return http.get()
                .uri("/check/{id}/attachment", checkId)
                .retrieve()
                .body(byte[].class);
    }

    public List<CheckbookCheck> listChecks() {
        requireKeys();
        CheckList list = http.get()
                .uri("/check")
                .retrieve()
                .body(CheckList.class);
        return list == null || list.checks() == null ? List.of() : list.checks();
    }

    // Checkbook rejects a chunked body ("Fields must be in JSON body"); a byte[] body carries a Content-Length.
    private byte[] toJsonBytes(Object body) {
        try {
            return json.writeValueAsBytes(body);
        } catch (IOException unserializable) {
            throw new IllegalArgumentException("cannot serialize Checkbook request body", unserializable);
        }
    }

    public CheckbookEnvironment environment() {
        return properties.environment();
    }

    private void requireKeys() {
        if (!properties.hasKeys()) {
            String variables = properties.environment() == CheckbookEnvironment.PRODUCTION
                    ? PRODUCTION_KEY_VARIABLES : SANDBOX_KEY_VARIABLES;
            throw new IllegalStateException(MISSING_KEYS_MESSAGE.formatted(properties.environment(), variables));
        }
    }

    private void requireKeysAndArmed() {
        requireKeys();
        if (!properties.mayMoveMoney()) {
            throw new IllegalStateException(UNARMED_MESSAGE);
        }
    }

    private CheckbookException toException(int status, byte[] body) {
        String error = "no error body";
        Map<String, String> moreInfo = new HashMap<>();
        try {
            JsonNode root = json.readTree(body);
            if (root != null && root.hasNonNull("error")) {
                error = root.get("error").asText();
            }
            if (root != null && root.path("more_info").isObject()) {
                root.get("more_info").fields().forEachRemaining(
                        field -> moreInfo.put(field.getKey(), field.getValue().asText()));
            }
        } catch (IOException notJson) {
            error = "unparseable error body";
        }
        return new CheckbookException(status, error, moreInfo);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CheckList(List<CheckbookCheck> checks) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WalletList(List<CheckbookWallet> wallets) {
    }

    private record CreateWalletRequest(String name, WalletType type) {
    }
}
