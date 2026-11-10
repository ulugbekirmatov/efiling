package com.irs.mef.inspector.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.irs.mef.ack.AckSource;
import com.irs.mef.ack.AckStore;
import com.irs.mef.ack.FileAckStore;
import com.irs.mef.ack.StoredAck;
import com.irs.mef.filing.IrsSession;
import com.irs.mef.inspector.FixtureSnapshots;
import com.irs.mef.inspector.ring.FileSendSnapshotRing;
import com.irs.mef.filing.Filing;
import com.irs.mef.filing.FilingOutcome;
import com.irs.mef.filing.TransmissionReceipt;
import com.irs.mef.filing.SubmissionRecord;
import com.irs.mef.filing.InMemoryJournal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InspectorSendsControllerTest {

    private static final String ID = FixtureSnapshots.SUBMISSION_ID;

    @TempDir
    Path temp;

    private FileSendSnapshotRing ring;
    private InMemoryJournal journal;
    private FileAckStore ackStore;
    private IrsSession session;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ring = new FileSendSnapshotRing(temp.resolve("ring"), 20, new ObjectMapper());
        journal = new InMemoryJournal();
        ackStore = new FileAckStore(temp.resolve("acks"), new ObjectMapper());
        session = mock(IrsSession.class);
        mvc = mvc(ackStore);
    }

    private MockMvc mvc(AckStore store) {
        ObjectMapper bootLikeMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        return MockMvcBuilders.standaloneSetup(
                        new InspectorSendsController(ring, journal, store, session))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(bootLikeMapper))
                .build();
    }

    @Test
    @DisplayName("list joins ring, journal and stored ack, newest first")
    void listJoinsRingJournalAndAck() throws Exception {
        ring.commit(FixtureSnapshots.orchidQ1());
        transmitted("QG6CDEPOSIT1");
        ackStore.record(storedAck("Accepted"));

        mvc.perform(get("/inspector/sends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].submissionId").value(ID))
                .andExpect(jsonPath("$[0].capturedAt").value("2026-03-31T12:00:00Z"))
                .andExpect(jsonPath("$[0].environment").value("ATS"))
                .andExpect(jsonPath("$[0].einMasked").value("*****0004"))
                .andExpect(jsonPath("$[0].formCode").value("941"))
                .andExpect(jsonPath("$[0].periodLabel").value("2026-01-01 to 2026-03-31"))
                .andExpect(jsonPath("$[0].state").value("TRANSMITTED"))
                .andExpect(jsonPath("$[0].depositId").value("QG6CDEPOSIT1"))
                .andExpect(jsonPath("$[0].ackType").value("Accepted"));
        verifyNoInteractions(session);
    }

    @Test
    @DisplayName("a send with no journal row and no ack lists with nulls; empty ring is []")
    void listWithoutJournalOrAck() throws Exception {
        mvc.perform(get("/inspector/sends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        ring.commit(FixtureSnapshots.orchidQ1());

        mvc.perform(get("/inspector/sends"))
                .andExpect(jsonPath("$[0].state").value(nullValue()))
                .andExpect(jsonPath("$[0].depositId").value(nullValue()))
                .andExpect(jsonPath("$[0].ackType").value(nullValue()));
    }

    @Test
    @DisplayName("detail returns pretty documents, transmission, MIME outline and the stored ack")
    void detail() throws Exception {
        ring.commit(FixtureSnapshots.orchidQ1());
        transmitted("QG6CDEPOSIT1");
        ackStore.record(storedAck("Accepted"));

        mvc.perform(get("/inspector/sends/" + ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissionId").value(ID))
                .andExpect(jsonPath("$.formType").value("941"))
                .andExpect(jsonPath("$.taxPeriodBegin").value("2026-01-01"))
                .andExpect(jsonPath("$.taxPeriodEnd").value("2026-03-31"))
                .andExpect(jsonPath("$.clientRequestId").value("inspector-demo-1"))
                .andExpect(jsonPath("$.transmission.state").value("TRANSMITTED"))
                .andExpect(jsonPath("$.transmission.depositId").value("QG6CDEPOSIT1"))
                .andExpect(jsonPath("$.transmission.createdAt").value("2026-03-31T12:00:00Z"))
                .andExpect(jsonPath("$.transmission.completedAt").value("2026-03-31T12:00:05Z"))
                .andExpect(jsonPath("$.transmission.receiptTimestamp").value("2026-03-31T12:00:04Z"))
                .andExpect(jsonPath("$.transmission.faultCode").value(nullValue()))
                .andExpect(jsonPath("$.documents.returnXml.text").value(containsString("<EIN>003000004</EIN>")))
                .andExpect(jsonPath("$.documents.returnXml.missingReason").value(nullValue()))
                .andExpect(jsonPath("$.documents.soapRequest.text").value(containsString("SendSubmissionsRequest")))
                .andExpect(jsonPath("$.documents.soapResponse.text").value(nullValue()))
                .andExpect(jsonPath("$.documents.soapResponse.missingReason").value("no inbound SOAP"))
                .andExpect(jsonPath("$.mime.contentType").value(containsString("multipart/related")))
                .andExpect(jsonPath("$.mime.attachments[0].contentId").value("<SubmissionsAttBin>"))
                .andExpect(jsonPath("$.mime.attachments[0].byteLength").value(377))
                .andExpect(jsonPath("$.ack.ackType").value("Accepted"))
                .andExpect(jsonPath("$.ack.source").value("GetNewAcks"))
                .andExpect(jsonPath("$.ack.retrievedAt").value("2026-09-25T09:26:34Z"));
    }

    @Test
    @DisplayName("detail without journal row or ack has null transmission and ack")
    void detailWithoutJournalOrAck() throws Exception {
        ring.commit(FixtureSnapshots.orchidQ1());

        mvc.perform(get("/inspector/sends/" + ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transmission").value(nullValue()))
                .andExpect(jsonPath("$.ack").value(nullValue()));
    }

    @Test
    @DisplayName("detail: malformed id is 400, id outside the ring is 404")
    void detailErrors() throws Exception {
        mvc.perform(get("/inspector/sends/not-a-submission-id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SUBMISSION_ID"));

        mvc.perform(get("/inspector/sends/" + ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEND_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST ack returns the stored ack from GetAck")
    void postAckReturnsStoredAck() throws Exception {
        StoredAck fresh = stored(ID, "Rejected", Instant.parse("2026-09-25T10:00:00Z"),
                AckSource.GET_ACK, List.of("X0000-008"));
        when(session.getAck(any())).thenReturn(Optional.of(fresh));

        mvc.perform(post("/inspector/sends/" + ID + "/ack"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissionId").value(ID))
                .andExpect(jsonPath("$.source").value("GetAck"))
                .andExpect(jsonPath("$.ackType").value("Rejected"))
                .andExpect(jsonPath("$.errorCodes[0]").value("X0000-008"))
                .andExpect(jsonPath("$.errorMessages", hasSize(0)))
                .andExpect(jsonPath("$.retrievedAt").value("2026-09-25T10:00:00Z"));
    }

    @Test
    @DisplayName("POST ack maps no ack yet to 404 and other IRS errors to 502 without echoing the cause")
    void postAckErrorMapping() throws Exception {
        when(session.getAck(any()))
                .thenReturn(Optional.empty())
                .thenThrow(new IllegalStateException("Keystore file does not exist at: /secret/pyramos.p12"))
                .thenThrow(new RuntimeException("IRS session limit - retryable"));

        mvc.perform(post("/inspector/sends/" + ID + "/ack"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACK_NOT_FOUND"));
        mvc.perform(post("/inspector/sends/" + ID + "/ack"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("ACK_RETRIEVAL_FAILED"))
                .andExpect(jsonPath("$.message").value("Acknowledgment retrieval failed; see server logs"));
        mvc.perform(post("/inspector/sends/" + ID + "/ack"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("ACK_RETRIEVAL_FAILED"));
    }

    @Test
    @DisplayName("POST ack surfaces a failed store write instead of reporting a stored ack")
    void postAckStoreFailureSurfaces() throws Exception {
        AckStore broken = new AckStore() {
            @Override
            public void record(StoredAck ack) {
                throw new IllegalStateException("disk full");
            }

            @Override
            public java.util.Optional<StoredAck> find(String submissionId) {
                return java.util.Optional.empty();
            }
        };
        when(session.getAck(any())).thenReturn(Optional.of(
                stored(ID, "Accepted", Instant.parse("2026-09-25T10:00:00Z"), AckSource.GET_ACK, List.of())));

        mvc(broken).perform(post("/inspector/sends/" + ID + "/ack"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("ACK_RETRIEVAL_FAILED"))
                .andExpect(jsonPath("$.message").value("Acknowledgment retrieval failed; see server logs"));
    }

    @Test
    @DisplayName("POST ack with a malformed id is 400 and never reaches IRS")
    void postAckMalformedId() throws Exception {
        mvc.perform(post("/inspector/sends/bad/ack"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SUBMISSION_ID"));
        verifyNoInteractions(session);
    }

    private void transmitted(String depositId) {
        Filing filing = FixtureSnapshots.orchidFiling();
        journal.reserve(SubmissionRecord.created(filing, "ATS"));
        TransmissionReceipt receipt = new TransmissionReceipt(depositId, filing.submissionId(),
                Instant.parse("2026-03-31T12:00:04Z"), TransmissionReceipt.TimestampSource.IRS, 1);
        journal.complete(filing.submissionId(), new FilingOutcome.Transmitted(receipt),
                Instant.parse("2026-03-31T12:00:05Z"));
    }

    private static StoredAck storedAck(String ackType) {
        return stored(ID, ackType, Instant.parse("2026-09-25T09:26:34Z"), AckSource.GET_NEW_ACKS, null);
    }

    private static StoredAck stored(String submissionId, String ackType, Instant retrievedAt, AckSource source,
                                    List<String> errorCodes) {
        return new StoredAck(
                null, submissionId, retrievedAt, source, ackType, null, null,
                errorCodes, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null);
    }
}
