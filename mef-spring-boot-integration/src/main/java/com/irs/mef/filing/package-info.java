/**
 * SendSubmissions is not idempotent at the IRS, so this package does not retry it.
 * Identical client bytes replay the journaled receipt and transmit nothing.
 * {@link JournalState#ABANDONED} is the only state that releases that key, and only
 * when the wire was never touched. A crash promotes a lingering CREATED row to
 * {@link JournalState#INDETERMINATE}.
 */
package com.irs.mef.filing;
