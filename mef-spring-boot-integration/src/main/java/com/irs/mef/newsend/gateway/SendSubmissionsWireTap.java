package com.irs.mef.newsend.gateway;

import com.irs.mef.newsend.domain.NewSendFiling;

/**
 * Optional wire observer for one SendSubmissions invoke.
 * Hidden: SAAJ, handler chains, redaction, disk, eviction, pretty-print.
 * Exposed: open a session around the invoke you already do.
 *
 * No-op implementation when mef.inspector.enabled=false.
 * Must never throw into the gateway on close() — capture is best-effort.
 */
public interface SendSubmissionsWireTap {

    Session open(NewSendFiling filing);

    interface Session extends AutoCloseable {
        /** Reflective attach onto the live SendSubmissionsClient. Idempotent. */
        void attach(Object sendSubmissionsClient);

        @Override
        void close();
    }
}
