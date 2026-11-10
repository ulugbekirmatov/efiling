package com.irs.mef.filing;


/** Used when {@code mef.inspector.enabled=false}. attach/close are empty. */
public final class NoOpSendSubmissionsWireTap implements SendSubmissionsWireTap {

    public static final NoOpSendSubmissionsWireTap INSTANCE = new NoOpSendSubmissionsWireTap();

    @Override
    public Session open(Filing filing) {
        return NoOpSession.INSTANCE;
    }

    private enum NoOpSession implements Session {
        INSTANCE;

        @Override
        public void attach(Object sendSubmissionsClient) {
            // capture disabled
        }

        @Override
        public void close() {
            // capture disabled
        }
    }
}
