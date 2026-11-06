package com.irs.mef.checkbook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** A Checkbook check as returned by create, get, and list. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CheckbookCheck(
        String id,
        String status,
        BigDecimal amount,
        String name,
        String recipient,
        String number,
        String date,
        String description) {
}
