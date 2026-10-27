package com.irs.mef.inspector.capture;

import com.irs.mef.inspector.ring.MimeRequest;
import com.irs.mef.inspector.ring.SendSnapshotRing;
import com.irs.mef.inspector.ring.SoapCapture;
import com.irs.mef.filing.Filing;
import com.irs.mef.filing.SendSubmissionsWireTap;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;

@Slf4j
public final class RingSendSubmissionsWireTap implements SendSubmissionsWireTap {

    private final SendSnapshotRing ring;
    private final ReflectiveMimeTap mimeTap;
    private final Clock clock;
    private final String environment;
    private final long maxXmlBytes;

    public RingSendSubmissionsWireTap(SendSnapshotRing ring,
                                      ReflectiveMimeTap mimeTap,
                                      Clock clock,
                                      String environment,
                                      long maxXmlBytes) {
        this.ring = ring;
        this.mimeTap = mimeTap;
        this.clock = clock;
        this.environment = environment;
        this.maxXmlBytes = maxXmlBytes;
    }

    @Override
    public Session open(Filing filing) {
        try {
            return new CaptureSession(filing, ring, mimeTap, clock, environment, maxXmlBytes);
        } catch (RuntimeException e) {
            log.warn("inspector: open failed, using no-op session: {}", e.toString());
            return com.irs.mef.filing.NoOpSendSubmissionsWireTap.INSTANCE.open(filing);
        }
    }
}
