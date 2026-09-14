package com.irs.mef.inspector.capture;

import com.irs.mef.inspector.FixtureSnapshots;
import com.irs.mef.inspector.ring.InMemorySendSnapshotRing;
import com.irs.mef.inspector.ring.SendSnapshot;
import com.irs.mef.inspector.ring.SendSnapshotRing;
import com.irs.mef.inspector.ring.SnapshotId;
import com.irs.mef.newsend.domain.NewSendFiling;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingSendSubmissionsWireTapTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-31T12:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("close stores packed Return XML even when the handler cannot attach")
    void closeStoresReturnXmlWhenHandlerMisses() {
        InMemorySendSnapshotRing ring = new InMemorySendSnapshotRing(20);
        RingSendSubmissionsWireTap tap = new RingSendSubmissionsWireTap(
                ring, new ReflectiveMimeTap(), CLOCK, "ATS", 1_048_576L);
        NewSendFiling filing = FixtureSnapshots.orchidFiling();

        try (var session = tap.open(filing)) {
            session.attach(new Object());
        }

        SendSnapshot snap = ring.get(new SnapshotId(filing.submissionId().value())).orElseThrow();
        assertEquals(filing.command().returnXml(), snap.returnXml().original());
        assertEquals(filing.command().returnXmlSha256(), snap.returnXmlSha256());
        assertTrue(snap.mimeRequest().soapPart().isEmpty());
        assertTrue(snap.summary().einMasked().endsWith("0004"));
    }

    @Test
    @DisplayName("disk failure on commit does not throw into the IRS send")
    void closeSwallowsRingFailures() {
        SendSnapshotRing throwing = new SendSnapshotRing() {
            @Override
            public List<com.irs.mef.inspector.ring.SnapshotSummary> listNewestFirst() {
                return List.of();
            }

            @Override
            public java.util.Optional<SendSnapshot> get(SnapshotId id) {
                return java.util.Optional.empty();
            }

            @Override
            public void commit(SendSnapshot snapshot) {
                throw new IllegalStateException("disk full");
            }

            @Override
            public int size() {
                return 0;
            }
        };
        RingSendSubmissionsWireTap tap = new RingSendSubmissionsWireTap(
                throwing, new ReflectiveMimeTap(), CLOCK, "ATS", 1_048_576L);
        assertDoesNotThrow(() -> {
            try (var session = tap.open(FixtureSnapshots.orchidFiling())) {
                session.attach(new Object());
            }
        });
    }

    @Test
    @DisplayName("oversize Return XML is skipped with a log, not thrown")
    void oversizeReturnXmlIsSkipped() {
        InMemorySendSnapshotRing ring = new InMemorySendSnapshotRing(20);
        RingSendSubmissionsWireTap tap = new RingSendSubmissionsWireTap(
                ring, new ReflectiveMimeTap(), CLOCK, "ATS", 32L);
        assertDoesNotThrow(() -> {
            try (var session = tap.open(FixtureSnapshots.orchidFiling())) {
                session.attach(new Object());
            }
        });
        assertEquals(0, ring.size());
    }

    @Test
    @DisplayName("attach on a BindingProvider is idempotent and does not throw")
    void attachOnBindingProviderIsIdempotent() {
        jakarta.xml.ws.Binding binding = new FakeBinding();
        jakarta.xml.ws.BindingProvider provider = new FakeBindingProvider(binding);
        InMemorySendSnapshotRing ring = new InMemorySendSnapshotRing(20);
        ReflectiveMimeTap mimeTap = new ReflectiveMimeTap();
        RingSendSubmissionsWireTap tap = new RingSendSubmissionsWireTap(
                ring, mimeTap, CLOCK, "ATS", 1_048_576L);
        try (var session = tap.open(FixtureSnapshots.orchidFiling())) {
            session.attach(provider);
            session.attach(provider);
        }
        assertEquals(1, binding.getHandlerChain().size());
        assertEquals(1, ring.size());
    }

    private static final class FakeBinding implements jakarta.xml.ws.Binding {
        private java.util.List<jakarta.xml.ws.handler.Handler> chain = new java.util.ArrayList<>();

        @Override
        public java.util.List<jakarta.xml.ws.handler.Handler> getHandlerChain() {
            return chain;
        }

        @Override
        public void setHandlerChain(java.util.List<jakarta.xml.ws.handler.Handler> chain) {
            this.chain = new java.util.ArrayList<>(chain);
        }

        @Override
        public String getBindingID() {
            return "fake";
        }
    }

    private static final class FakeBindingProvider implements jakarta.xml.ws.BindingProvider {
        private final jakarta.xml.ws.Binding binding;

        FakeBindingProvider(jakarta.xml.ws.Binding binding) {
            this.binding = binding;
        }

        @Override
        public java.util.Map<String, Object> getRequestContext() {
            return java.util.Map.of();
        }

        @Override
        public java.util.Map<String, Object> getResponseContext() {
            return java.util.Map.of();
        }

        @Override
        public jakarta.xml.ws.Binding getBinding() {
            return binding;
        }

        @Override
        public jakarta.xml.ws.EndpointReference getEndpointReference() {
            return null;
        }

        @Override
        public <T extends jakarta.xml.ws.EndpointReference> T getEndpointReference(Class<T> clazz) {
            return null;
        }
    }
}
