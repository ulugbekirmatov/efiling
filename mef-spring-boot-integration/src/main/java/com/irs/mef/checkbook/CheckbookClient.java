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

/** Checkbook.io API client, sandbox only. */
@Component
@EnableConfigurationProperties(CheckbookProperties.class)
public class CheckbookClient {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String MISSING_KEYS_MESSAGE =
            "Checkbook keys are not set: export MEF_CHECKBOOK_PUBLISHABLE_KEY and MEF_CHECKBOOK_SECRET_KEY";

    private final RestClient http;
    private final CheckbookProperties properties;
    private final ObjectMapper json = new ObjectMapper();

    public CheckbookClient(RestClient.Builder builder, CheckbookProperties properties) {
        this.properties = properties;
        this.http = builder
                .baseUrl(CheckbookEnvironment.SANDBOX.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, properties.publishableKey() + ":" + properties.secretKey())
                .defaultStatusHandler(status -> !status.is2xxSuccessful(), (req, res) -> {
                    throw toException(res.getStatusCode().value(), res.getBody().readAllBytes());
                })
                .build();
    }

    public CheckbookCheck sendDigitalCheck(DigitalCheckRequest request, String idempotencyKey) {
        requireKeys();
        return http.post()
                .uri("/check/digital")
                .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(CheckbookCheck.class);
    }

    public CheckbookCheck getCheck(String id) {
        requireKeys();
        return http.get()
                .uri("/check/{id}", id)
                .retrieve()
                .body(CheckbookCheck.class);
    }

    public List<CheckbookCheck> listChecks() {
        requireKeys();
        CheckList list = http.get()
                .uri("/check")
                .retrieve()
                .body(CheckList.class);
        return list == null || list.checks() == null ? List.of() : list.checks();
    }

    private void requireKeys() {
        if (!properties.hasKeys()) {
            throw new IllegalStateException(MISSING_KEYS_MESSAGE);
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
}
