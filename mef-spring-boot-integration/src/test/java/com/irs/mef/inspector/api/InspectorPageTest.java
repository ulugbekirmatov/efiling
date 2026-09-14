package com.irs.mef.inspector.api;

import com.irs.mef.inspector.FixtureSnapshots;
import com.irs.mef.inspector.ring.SendSnapshot;
import com.irs.mef.inspector.ring.SendSnapshotRing;
import com.irs.mef.inspector.view.InspectorHtml;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InspectorPageController.class)
@Import(InspectorHtml.class)
class InspectorPageTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    SendSnapshotRing ring;

    @Test
    @DisplayName("operator can pick a send and read Return.xml plus MIME outline without SAML")
    void operatorCanPickASendAndReadReturnAndMime() throws Exception {
        SendSnapshot snap = FixtureSnapshots.orchidQ1();
        when(ring.listNewestFirst()).thenReturn(List.of(snap.summary()));
        when(ring.get(snap.submissionId())).thenReturn(Optional.of(snap));

        mvc.perform(get("/inspector").param("id", snap.submissionId().value()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("Return.xml")))
                .andExpect(content().string(containsString("multipart/related")))
                .andExpect(content().string(containsString("octet-stream omitted")))
                .andExpect(content().string(containsString("003000004")))
                .andExpect(content().string(not(containsString("saml:Assertion"))))
                .andExpect(content().string(not(containsString("samlAssertion"))))
                .andExpect(content().string(not(containsString("/mef/auth/login"))));
    }

    @Test
    @DisplayName("empty ring shows the last-N empty state, never 404")
    void emptyState() throws Exception {
        when(ring.listNewestFirst()).thenReturn(List.of());
        when(ring.get(any())).thenReturn(Optional.empty());

        mvc.perform(get("/inspector"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nothing captured on this host yet")))
                .andExpect(content().string(containsString("not an IRS archive")));
    }

    @Test
    @DisplayName("unknown or invalid id is a missing pane, not HTTP 500")
    void unknownIdIsMissingPane() throws Exception {
        SendSnapshot snap = FixtureSnapshots.orchidQ1();
        when(ring.listNewestFirst()).thenReturn(List.of(snap.summary()));
        when(ring.get(any())).thenReturn(Optional.empty());

        mvc.perform(get("/inspector").param("id", "not-a-submission-id"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("not in the last")))
                .andExpect(content().string(containsString(snap.submissionId().value())));
    }
}
