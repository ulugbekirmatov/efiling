package com.irs.mef.filing;

/**
 * Journal state machine. ABANDONED and REJECTED release the idempotency key.
 * ABANDONED is reachable only from failures that provably never touched the wire.
 * REJECTED is a SOAP fault at SendSubmissions: the IRS refused the request and filed
 * nothing, so the same bytes may reserve a new submission id.
 *
 * <pre>
 *   CREATED ── pre-flight failure ──&gt; ABANDONED      (key reusable: nothing was filed)
 *   CREATED ── invoke returned ok ──&gt; TRANSMITTED    (terminal; replay, no IRS call)
 *   CREATED ── SOAP fault ─────────&gt; REJECTED        (key reusable: nothing was filed)
 *   CREATED ── anything else ──────&gt; INDETERMINATE   (terminal; replay, no IRS call)
 *   CREATED ── JVM crash ──────────&gt; INDETERMINATE   (applied by the journal on startup replay)
 * </pre>
 */
public enum JournalState {
    CREATED,
    TRANSMITTED,
    REJECTED,
    INDETERMINATE,
    ABANDONED
}
