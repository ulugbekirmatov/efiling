package com.irs.mef.newsend.gateway;

import com.irs.mef.newsend.domain.NewSendFault;

/**
 * The submission container could not be composed, or the reflective dispatch failed before the
 * SDK method ran. Provably pre-wire: the IRS never saw anything, so the caller may abandon the
 * journal row and release the idempotency key.
 */
public class NewSendCompositionException extends RuntimeException {

    private final transient NewSendFault fault;

    public NewSendCompositionException(NewSendFault fault, Throwable cause) {
        super(fault.message(), cause);
        this.fault = fault;
    }

    public NewSendFault fault() {
        return fault;
    }
}
