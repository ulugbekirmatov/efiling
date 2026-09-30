package com.irs.mef.ack;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AckSource {
    GET_ACK("GetAck"),
    GET_NEW_ACKS("GetNewAcks");

    private final String operationName;

    AckSource(String operationName) {
        this.operationName = operationName;
    }

    @JsonValue
    public String operationName() {
        return operationName;
    }
}
