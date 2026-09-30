package com.irs.mef.ack;

import java.util.Optional;

/**
 * Never evicts: IRS requires acknowledgment retention. {@link #record} replaces any earlier ack for the
 * same submission id.
 */
public interface AckStore {

    void record(StoredAck ack);

    Optional<StoredAck> find(String submissionId);
}
