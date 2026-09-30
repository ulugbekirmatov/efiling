package com.irs.mef.inspector.api;

import com.irs.mef.ack.AckSource;
import com.irs.mef.ack.AckStore;
import com.irs.mef.ack.StoredAck;
import com.irs.mef.dto.AckResponse;
import com.irs.mef.exception.MefException;
import com.irs.mef.inspector.ring.SendSnapshotRing;
import com.irs.mef.newsend.journal.NewSendSubmissionJournal;
import com.irs.mef.service.AcknowledgementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InspectorSendsController.class)
class InspectorSendsWebMvcTest {

    private static final String ID = "2378612026268yagqidh";

    @Autowired
    MockMvc mvc;

    @MockBean
    SendSnapshotRing ring;

    @MockBean
    NewSendSubmissionJournal journal;

    @MockBean
    AckStore ackStore;

    @MockBean
    AcknowledgementService acknowledgementService;

    @Test
    @DisplayName("Boot's mapper writes retrievedAt as ISO-8601 and source as the IRS operation name")
    void bootMapperWritesIsoInstants() throws Exception {
        when(acknowledgementService.retrieveAndRecordAcknowledgment(ID)).thenReturn(StoredAck.from(
                AckResponse.builder().submissionId(ID).ackType("Accepted").build(),
                Instant.parse("2026-09-25T09:26:34Z"), AckSource.GET_ACK));

        mvc.perform(post("/inspector/sends/" + ID + "/ack"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retrievedAt").value("2026-09-25T09:26:34Z"))
                .andExpect(jsonPath("$.source").value("GetAck"));
    }

    @Test
    @DisplayName("NOT_LOGGED_IN is 409 here even though GlobalExceptionHandler maps MefException to 500")
    void globalHandlerDoesNotOverrideMapping() throws Exception {
        when(acknowledgementService.retrieveAndRecordAcknowledgment(ID))
                .thenThrow(new MefException("NOT_LOGGED_IN", "Not logged in to IRS MeF", "login first"));

        mvc.perform(post("/inspector/sends/" + ID + "/ack"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_LOGGED_IN"));
    }
}
