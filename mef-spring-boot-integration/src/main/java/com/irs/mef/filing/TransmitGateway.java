package com.irs.mef.filing;

import com.irs.mef.ack.StoredAck;

import java.util.Optional;

interface TransmitGateway {

    FilingOutcome transmit(Object serviceContext, Preflight.Cleared cleared);

    Optional<StoredAck> getAck(Object serviceContext, String submissionId);
}
