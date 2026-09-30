package com.irs.mef.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irs.mef.ack.AckSource;
import com.irs.mef.ack.AckStore;
import com.irs.mef.ack.FileAckStore;
import com.irs.mef.ack.StoredAck;
import com.irs.mef.config.MefSdkConfig;
import com.irs.mef.dto.AckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AcknowledgementServiceAckStoreTest {

    private static final Instant NOW = Instant.parse("2026-09-25T09:26:34Z");
    private static final String ORCHID = "2378612026268yagqidh";
    private static final String OTHER = "2378612026268zzzzzzz";

    @TempDir
    Path temp;

    private MefClientService mefClientService;
    private RetryTemplate retryTemplate;

    @BeforeEach
    void setUp() {
        mefClientService = mock(MefClientService.class);
        when(mefClientService.isLoggedIn()).thenReturn(true);
        retryTemplate = mock(RetryTemplate.class);
    }

    @Test
    @DisplayName("every GetNewAcks ack with a submission id is stored with source GetNewAcks")
    void getNewAcksRecordsEachAck() {
        FileAckStore store = new FileAckStore(temp, new ObjectMapper());
        irsReturns(AckResponse.AckListResponse.builder()
                .acknowledgments(List.of(ack(ORCHID, "Accepted"), ack(OTHER, "Rejected"), ack(null, "Accepted")))
                .totalCount(3)
                .build());

        AckResponse.AckListResponse result = service(store).getNewAcknowledgments();

        assertEquals(3, result.getTotalCount());
        StoredAck stored = store.find(ORCHID).orElseThrow();
        assertEquals("Accepted", stored.ackType());
        assertEquals(AckSource.GET_NEW_ACKS, stored.source());
        assertEquals(NOW, stored.retrievedAt());
        assertEquals("Rejected", store.find(OTHER).orElseThrow().ackType());
    }

    @Test
    @DisplayName("a store that fails to write never fails the IRS call")
    void storeFailureDoesNotFailTheCall() {
        AckStore broken = new AckStore() {
            @Override
            public void record(StoredAck ack) {
                throw new IllegalStateException("disk full");
            }

            @Override
            public Optional<StoredAck> find(String submissionId) {
                return Optional.empty();
            }
        };
        irsReturns(AckResponse.AckListResponse.builder()
                .acknowledgments(List.of(ack(ORCHID, "Accepted")))
                .totalCount(1)
                .build());

        AckResponse.AckListResponse result = service(broken).getNewAcknowledgments();

        assertEquals("Accepted", result.getAcknowledgments().get(0).getAckType());
    }

    @Test
    @DisplayName("GetAck for the viewer surfaces a failed write instead of reporting a stored ack")
    void getAckStoreFailureSurfaces() {
        AckStore broken = new AckStore() {
            @Override
            public void record(StoredAck ack) {
                throw new IllegalStateException("disk full");
            }

            @Override
            public Optional<StoredAck> find(String submissionId) {
                return Optional.empty();
            }
        };
        irsReturns(ack(ORCHID, "Accepted"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service(broken).retrieveAndRecordAcknowledgment(ORCHID));

        assertEquals("disk full", thrown.getMessage());
    }

    @SuppressWarnings("unchecked")
    private void irsReturns(Object response) {
        when(retryTemplate.execute(any(RetryCallback.class))).thenReturn(response);
    }

    private AcknowledgementService service(AckStore store) {
        return new AcknowledgementService(mefClientService, new MefSdkConfig(), retryTemplate, store,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AckResponse ack(String submissionId, String ackType) {
        return AckResponse.builder().submissionId(submissionId).ackType(ackType).build();
    }
}
