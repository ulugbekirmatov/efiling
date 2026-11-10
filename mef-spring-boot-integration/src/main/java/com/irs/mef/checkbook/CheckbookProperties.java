package com.irs.mef.checkbook;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Binds {@code checkbook.*}. The sandbox keys sit at the top level and the production keys under
 * {@code checkbook.production}, so both can live in one {@code .env}. {@code environment} picks the pair.
 * Production POSTs also need {@code checkbook.production.armed=true}: a stray environment switch alone
 * must not move money.
 */
@ConfigurationProperties("checkbook")
public record CheckbookProperties(@DefaultValue("SANDBOX") CheckbookEnvironment environment,
                                  String publishableKey, String secretKey,
                                  @DefaultValue Production production) {

    public record Production(String publishableKey, String secretKey, boolean armed) {

        @Override
        public String toString() {
            return "Production[publishableKey=" + publishableKey + ", secretKey=****, armed=" + armed + "]";
        }
    }

    public static CheckbookProperties sandbox(String publishableKey, String secretKey) {
        return new CheckbookProperties(CheckbookEnvironment.SANDBOX, publishableKey, secretKey,
                new Production(null, null, false));
    }

    public static CheckbookProperties production(String publishableKey, String secretKey, boolean armed) {
        return new CheckbookProperties(CheckbookEnvironment.PRODUCTION, null, null,
                new Production(publishableKey, secretKey, armed));
    }

    String activePublishableKey() {
        return environment == CheckbookEnvironment.PRODUCTION ? production.publishableKey() : publishableKey;
    }

    String activeSecretKey() {
        return environment == CheckbookEnvironment.PRODUCTION ? production.secretKey() : secretKey;
    }

    boolean hasKeys() {
        return !isBlank(activePublishableKey()) && !isBlank(activeSecretKey());
    }

    boolean mayMoveMoney() {
        return environment == CheckbookEnvironment.SANDBOX || production.armed();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public String toString() {
        return "CheckbookProperties[environment=" + environment + ", publishableKey=" + publishableKey
                + ", secretKey=****, production=" + production + "]";
    }
}
