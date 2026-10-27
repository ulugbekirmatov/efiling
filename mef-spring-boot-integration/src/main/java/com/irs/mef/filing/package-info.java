/**
 * SendSubmissions is not idempotent at the IRS, so this package does not retry it.
 * Identical client bytes replay the journaled receipt and transmit nothing while the
 * row is {@link JournalState#TRANSMITTED} or {@link JournalState#INDETERMINATE}.
 * {@link JournalState#ABANDONED} and {@link JournalState#REJECTED} release that key:
 * ABANDONED when the wire was never touched, REJECTED when SendSubmissions returned a
 * SOAP fault and the IRS filed nothing. A crash promotes a lingering CREATED row to
 * {@link JournalState#INDETERMINATE}.
 */
package com.irs.mef.filing;
