package com.irs.mef.newsend.domain;

/**
 * Journal state machine. ABANDONED is the ONLY state that releases the idempotency key,
 * and it is reachable only from failures that provably never touched the wire.
 *
 * <pre>
 *   CREATED ── pre-flight failure ──&gt; ABANDONED      (key reusable: nothing was filed)
 *   CREATED ── invoke returned ok ──&gt; TRANSMITTED    (terminal)
 *   CREATED ── IRS business fault ──&gt; REJECTED       (terminal; re-file needs a new key)
 *   CREATED ── anything else ──────&gt; INDETERMINATE   (terminal; human/ack reconciliation)
 *   CREATED ── JVM crash ──────────&gt; INDETERMINATE   (applied by the journal on startup replay)
 * </pre>
 */
public enum NewSendState {
    CREATED,
    TRANSMITTED,
    REJECTED,
    INDETERMINATE,
    ABANDONED
}
