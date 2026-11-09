package com.irs.mef.checkbook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** A Checkbook wallet. The account and routing numbers Checkbook returns are deliberately not mapped, so they are never held or logged. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CheckbookWallet(String id, String name, BigDecimal balance, String date) {
}
