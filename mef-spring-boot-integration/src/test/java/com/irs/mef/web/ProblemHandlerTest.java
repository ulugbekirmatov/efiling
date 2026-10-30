package com.irs.mef.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irs.mef.filing.ClientFiling;
import com.irs.mef.filing.FilingDesk;
import com.irs.mef.filing.IrsLoginException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FilingController.class)
class ProblemHandlerTest {

    private static String body() throws Exception {
        String xml = Files.readString(Path.of(
                "../test-scenarios/ats-ty2026/scenario-1-orchid-941/client-return-body.xml"));
        return new ObjectMapper().writeValueAsString(Map.of("clientId", "orchid", "clientBody", xml));
    }

    @Autowired
    MockMvc mvc;

    @MockBean
    FilingDesk desk;

    @Test
    void loginFailureIs503WithAFixedMessage() throws Exception {
        when(desk.file(any())).thenThrow(new IrsLoginException(
                "Keystore file does not exist at: /secret/keystore.p12"));

        mvc.perform(post("/mef/filings").contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("IRS_LOGIN_FAILED"))
                .andExpect(jsonPath("$.message").value("IRS login failed; see server logs"))
                .andExpect(content().string(not(containsString("keystore"))));
    }

    @Test
    void anUnexpectedErrorHidesTheExceptionMessage() throws Exception {
        when(desk.file(any())).thenThrow(new IllegalStateException(
                "Keystore file does not exist at: /var/secret/keystore.p12"));

        mvc.perform(post("/mef/filings").contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("FILING_INTERNAL"))
                .andExpect(jsonPath("$.message").value("Internal error; see server logs"))
                .andExpect(content().string(not(containsString("/var/secret"))));
    }

    @Test
    void anOverLimitBodyIs400AndDoesNotReachTheDesk() throws Exception {
        String xml = "x".repeat(ClientFiling.MAX_CLIENT_BODY_CHARS + 1);
        String json = new ObjectMapper().writeValueAsString(Map.of("clientId", "orchid", "clientBody", xml));

        mvc.perform(post("/mef/filings").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());

        verify(desk, never()).file(any());
    }

    @Test
    void allowDuplicatePeriodIsPassedToTheDesk() throws Exception {
        when(desk.file(any())).thenThrow(new IrsLoginException("stop"));
        String xml = Files.readString(Path.of(
                "../test-scenarios/ats-ty2026/scenario-1-orchid-941/client-return-body.xml"));
        String json = new ObjectMapper().writeValueAsString(Map.of(
                "clientId", "orchid", "clientBody", xml, "allowDuplicatePeriod", true));

        mvc.perform(post("/mef/filings").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isServiceUnavailable());

        ArgumentCaptor<ClientFiling> captor = ArgumentCaptor.forClass(ClientFiling.class);
        verify(desk).file(captor.capture());
        assertEquals(true, captor.getValue().allowDuplicatePeriod());
    }
}
