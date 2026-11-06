package com.irs.mef.inspector.api;

import com.irs.mef.ack.AckSource;
import com.irs.mef.ack.AckStore;
import com.irs.mef.ack.StoredAck;
import com.irs.mef.ats.AtsScenario;
import com.irs.mef.filing.IrsSession;
import com.irs.mef.inspector.FixtureSnapshots;
import com.irs.mef.inspector.ring.CapturedXml;
import com.irs.mef.inspector.ring.SendSnapshot;
import com.irs.mef.inspector.ring.SendSnapshotRing;
import com.irs.mef.filing.Journal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
    Journal journal;

    @MockBean
    AckStore ackStore;

    @MockBean
    IrsSession session;

    @Test
    @DisplayName("Boot's mapper writes retrievedAt as ISO-8601 and source as the IRS operation name")
    void bootMapperWritesIsoInstants() throws Exception {
        when(session.getAck(any())).thenReturn(Optional.of(stored("Accepted")));

        mvc.perform(post("/inspector/sends/" + ID + "/ack"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retrievedAt").value("2026-09-25T09:26:34Z"))
                .andExpect(jsonPath("$.source").value("GetAck"));
    }

    @Test
    @DisplayName("an ack retrieval failure is 502 from this controller")
    void retrievalFailureIsBadGateway() throws Exception {
        when(session.getAck(any())).thenThrow(new IllegalStateException("Failed to parse acknowledgment response"));

        mvc.perform(post("/inspector/sends/" + ID + "/ack"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("ACK_RETRIEVAL_FAILED"));
    }

    @Test
    @DisplayName("a captured send prints as a PDF of the IRS forms")
    void capturedSendPrintsAsPdf() throws Exception {
        String orchid = Files.readString(AtsScenario.ORCHID_941.clientBody().resolveSibling("composed-return.xml"));
        when(ring.get(any())).thenReturn(Optional.of(withReturnXml(FixtureSnapshots.orchidQ1(), orchid)));

        mvc.perform(get("/inspector/sends/" + FixtureSnapshots.SUBMISSION_ID + "/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(result -> org.assertj.core.api.Assertions
                        .assertThat(new String(result.getResponse().getContentAsByteArray(), 0, 5)).isEqualTo("%PDF-"));
    }

    @Test
    @DisplayName("a send outside the ring is 404 and a Return the renderer cannot print is 422")
    void missingAndUnprintableSends() throws Exception {
        when(ring.get(any())).thenReturn(Optional.empty());
        mvc.perform(get("/inspector/sends/" + ID + "/pdf"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEND_NOT_FOUND"));

        when(ring.get(any())).thenReturn(Optional.of(FixtureSnapshots.orchidQ1()));
        mvc.perform(get("/inspector/sends/" + FixtureSnapshots.SUBMISSION_ID + "/pdf"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PDF_NOT_PRINTABLE"));
    }

    private static SendSnapshot withReturnXml(SendSnapshot base, String returnXml) {
        CapturedXml captured = new CapturedXml(returnXml);
        return new SendSnapshot(base.submissionId(), base.capturedAt(), base.environment(), base.einMasked(),
                base.formType(), base.taxPeriod(), base.clientRequestId(), captured.sha256(), captured,
                base.manifestXml(), base.mimeRequest(), base.soapResponse());
    }

    private static StoredAck stored(String ackType) {
        return new StoredAck(
                null, ID, Instant.parse("2026-09-25T09:26:34Z"), AckSource.GET_ACK, ackType, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null);
    }
}
