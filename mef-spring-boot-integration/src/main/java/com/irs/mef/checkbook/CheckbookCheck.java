package com.irs.mef.checkbook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

/** A Checkbook check as returned by create, get, and list. {@code recipient} is text for a digital check and an address object for a mailed one. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CheckbookCheck(
        String id,
        String status,
        BigDecimal amount,
        String name,
        JsonNode recipient,
        String number,
        String date,
        String description) {
}
