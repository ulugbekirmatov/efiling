package com.irs.mef.checkbook;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds {@code checkbook.*}. Both keys stay blank until an operator sets the environment variables. */
@ConfigurationProperties("checkbook")
public record CheckbookProperties(String publishableKey, String secretKey) {

    boolean hasKeys() {
        return publishableKey != null && !publishableKey.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }

    @Override
    public String toString() {
        return "CheckbookProperties[publishableKey=" + publishableKey + ", secretKey=****]";
    }
}
