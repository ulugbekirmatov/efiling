package com.irs.mef.newsend.gateway;

import com.irs.mef.newsend.domain.NewSendFiling;
import com.irs.mef.newsend.domain.NewSendOutcome;

/**
 * The entire IRS wire boundary, behind one method.
 *
 * Hidden: the reflective SDK marshalling (Java 17 module-access workaround), the in-memory-only
 * constructor rule, receipt correlation, and fault classification.
 * Exposed: two parameters and a sealed return type. No gov.irs.* type crosses this line.
 *
 * Contract: NEVER throws for a wire fault — a wire fault is a {@link NewSendOutcome} value,
 * because "it threw" and "it was not filed" are different claims. It throws
 * {@link NewSendCompositionException} ONLY for failures that provably precede any network I/O.
 */
public interface NewSendTransmitGateway {

    /**
     * @param serviceContext the SINGLETON gov.irs.mef.services.ServiceContext from
     *                       MefClientService#getCurrentServiceContext(), passed as Object because
     *                       that is the existing accessor's type. A fresh one invalidates the session.
     * @throws NewSendCompositionException before any network I/O (safe to release the idempotency key)
     */
    NewSendOutcome transmit(Object serviceContext, NewSendFiling filing);
}
