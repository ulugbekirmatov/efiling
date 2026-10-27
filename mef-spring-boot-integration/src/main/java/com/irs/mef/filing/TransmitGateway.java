package com.irs.mef.filing;


/**
 * The entire IRS wire boundary, behind one method.
 *
 * Hidden: the reflective SDK marshalling (Java 17 module-access workaround), the in-memory-only
 * constructor rule, receipt correlation, and fault classification.
 * Exposed: two parameters and a sealed return type. No gov.irs.* type crosses this line.
 *
 * Contract: NEVER throws for a wire fault — a wire fault is a {@link FilingOutcome} value,
 * because "it threw" and "it was not filed" are different claims. It throws
 * {@link NewSendCompositionException} ONLY for failures that provably precede any network I/O.
 */
interface TransmitGateway {

    /**
     * @param serviceContext the ServiceContext from {@code IrsSession.call}, passed as Object
     *                       because the SDK type stays behind reflection. A fresh one invalidates the session.
     * @throws NewSendCompositionException before any network I/O (safe to release the idempotency key)
     */
    FilingOutcome transmit(Object serviceContext, Filing filing);
}
