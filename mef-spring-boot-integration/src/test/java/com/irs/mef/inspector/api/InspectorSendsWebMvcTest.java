package com.irs.mef.inspector.api;

import com.irs.mef.ack.AckSource;
import com.irs.mef.ack.AckStore;
import com.irs.mef.ack.StoredAck;
import com.irs.mef.filing.IrsSession;
import com.irs.mef.filing.IrsWire;
import com.irs.mef.inspector.ring.SendSnapshotRing;
import com.irs.mef.newsend.journal.NewSendSubmissionJournal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
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
    IrsSession session;

    @MockBean
    IrsWire wire;

    @Test
    @DisplayName("Boot's mapper writes retrievedAt as ISO-8601 and source as the IRS operation name")
    void bootMapperWritesIsoInstants() throws Exception {
        when(session.call(any())).thenReturn(Optional.of(stored("Accepted")));

        mvc.perform(post("/inspector/sends/" + ID + "/ack"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retrievedAt").value("2026-09-25T09:26:34Z"))
                .andExpect(jsonPath("$.source").value("GetAck"));
    }

    @Test
    @DisplayName("an ack retrieval failure is 502 from this controller")
    void retrievalFailureIsBadGateway() throws Exception {
        when(session.call(any())).thenThrow(new IllegalStateException("Failed to parse acknowledgment response"));

        mvc.perform(post("/inspector/sends/" + ID + "/ack"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("ACK_RETRIEVAL_FAILED"));
    }

    private static StoredAck stored(String ackType) {
        return new StoredAck(
                null, ID, Instant.parse("2026-09-25T09:26:34Z"), AckSource.GET_ACK, ackType, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null);
    }
}
