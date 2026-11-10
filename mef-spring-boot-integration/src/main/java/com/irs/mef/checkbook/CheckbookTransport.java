package com.irs.mef.checkbook;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * HTTP transport for {@link CheckbookClient}, its own type so it never replaces Boot's shared
 * {@code RestClient.Builder}. Without explicit timeouts Boot 3.2 falls back to a request factory that
 * can wait forever on a payment POST.
 */
public record CheckbookTransport(RestClient.Builder builder) {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    static CheckbookTransport withTimeouts() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return new CheckbookTransport(RestClient.builder().requestFactory(requestFactory));
    }

    @Configuration
    static class Beans {

        @Bean
        CheckbookTransport checkbookTransport() {
            return withTimeouts();
        }
    }
}
