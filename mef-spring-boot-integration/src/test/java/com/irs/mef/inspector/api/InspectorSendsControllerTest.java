package com.irs.mef.inspector.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.irs.mef.ack.AckSource;
import com.irs.mef.ack.FileAckStore;
import com.irs.mef.ack.StoredAck;
import com.irs.mef.dto.AckResponse;
import com.irs.mef.exception.MefException;
import com.irs.mef.inspector.FixtureSnapshots;
import com.irs.mef.inspector.ring.FileSendSnapshotRing;
import com.irs.mef.newsend.domain.NewSendFiling;
import com.irs.mef.newsend.domain.NewSendOutcome;
import com.irs.mef.newsend.domain.NewSendReceipt;
import com.irs.mef.newsend.domain.NewSendSubmissionRecord;
import com.irs.mef.newsend.journal.InMemoryNewSendSubmissionJournal;
import com.irs.mef.service.AcknowledgementService;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
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
    private InMemoryNewSendSubmissionJournal journal;
    private FileAckStore ackStore;
    private AcknowledgementService acknowledgementService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ring = new FileSendSnapshotRing(temp.resolve("ring"), 20, new ObjectMapper());
        journal = new InMemoryNewSendSubmissionJournal();
        ackStore = new FileAckStore(temp.resolve("acks"), new ObjectMapper());
        acknowledgementService = mock(AcknowledgementService.class);
        ObjectMapper bootLikeMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        mvc = MockMvcBuilders.standaloneSetup(
                        new InspectorSendsController(ring, journal, ackStore, acknowledgementService))
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
        verifyNoInteractions(acknowledgementService);
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
        StoredAck fresh = StoredAck.from(AckResponse.builder()
                .submissionId(ID)
                .ackType("Rejected")
                .errorCodes(List.of("X0000-008"))
                .build(), Instant.parse("2026-09-25T10:00:00Z"), AckSource.GET_ACK);
        when(acknowledgementService.retrieveAndRecordAcknowledgment(ID)).thenReturn(fresh);

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
    @DisplayName("POST ack maps not logged in to 409, no ack yet to 404, other IRS errors to 502")
    void postAckErrorMapping() throws Exception {
        when(acknowledgementService.retrieveAndRecordAcknowledgment(anyString()))
                .thenThrow(new MefException("NOT_LOGGED_IN", "Not logged in to IRS MeF", "login first"))
                .thenThrow(new MefException("ACK_NOT_FOUND", "Acknowledgment not found", "none yet"))
                .thenThrow(new MefException("ACK_PARSE_ERROR", "Failed to parse acknowledgment response", "x"))
                .thenThrow(new RuntimeException("IRS session limit - retryable"));

        mvc.perform(post("/inspector/sends/" + ID + "/ack"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_LOGGED_IN"))
                .andExpect(jsonPath("$.message").value("Not logged in to IRS MeF"));
        mvc.perform(post("/inspector/sends/" + ID + "/ack"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACK_NOT_FOUND"));
        mvc.perform(post("/inspector/sends/" + ID + "/ack"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("ACK_RETRIEVAL_FAILED"))
                .andExpect(jsonPath("$.message").value("Failed to parse acknowledgment response"));
        mvc.perform(post("/inspector/sends/" + ID + "/ack"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("ACK_RETRIEVAL_FAILED"));
    }

    @Test
    @DisplayName("POST ack with a malformed id is 400 and never reaches IRS")
    void postAckMalformedId() throws Exception {
        mvc.perform(post("/inspector/sends/bad/ack"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SUBMISSION_ID"));
        verifyNoInteractions(acknowledgementService);
    }

    private void transmitted(String depositId) {
        NewSendFiling filing = FixtureSnapshots.orchidFiling();
        journal.reserve(NewSendSubmissionRecord.created(filing, "ATS"));
        NewSendReceipt receipt = new NewSendReceipt(depositId, filing.submissionId(),
                Instant.parse("2026-03-31T12:00:04Z"), NewSendReceipt.TimestampSource.IRS, 1);
        journal.complete(filing.submissionId(), new NewSendOutcome.Transmitted(receipt),
                Instant.parse("2026-03-31T12:00:05Z"));
    }

    private static StoredAck storedAck(String ackType) {
        return StoredAck.from(AckResponse.builder()
                .submissionId(ID)
                .ackType(ackType)
                .build(), Instant.parse("2026-09-25T09:26:34Z"), AckSource.GET_NEW_ACKS);
    }
}
