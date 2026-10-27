package com.irs.mef.filing;


/**
 * The submission container could not be composed, or the reflective dispatch failed before the
 * SDK method ran. Provably pre-wire: the IRS never saw anything, so the caller may abandon the
 * journal row and release the idempotency key.
 */
class CompositionException extends RuntimeException {

    private final transient IrsFault fault;

    CompositionException(IrsFault fault, Throwable cause) {
        super(fault.message(), cause);
        this.fault = fault;
    }

    IrsFault fault() {
        return fault;
    }
}
