package com.irs.mef.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irs.mef.filing.ClientFiling;
import com.irs.mef.filing.FilingDesk;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FilingController.class)
@Import(FilingBodyLimits.class)
class FilingBodyLimitTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    FilingDesk desk;

    @Test
    void anOverLimitBodyWithoutContentLengthIsRefused() throws Exception {
        String xml = "x".repeat(ClientFiling.MAX_CLIENT_BODY_CHARS + 1);
        byte[] json = new ObjectMapper()
                .writeValueAsBytes(Map.of("clientId", "orchid", "clientBody", xml));

        mvc.perform(post("/mef/filings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(request -> {
                            request.setContent(json);
                            request.removeHeader(HttpHeaders.CONTENT_LENGTH);
                            return request;
                        }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("exceeds the maximum length")));

        verify(desk, never()).file(any());
    }

    @Test
    void anOverLimitContentLengthIs413() throws Exception {
        byte[] json = "{\"clientId\":\"orchid\",\"clientBody\":\"<Return/>\"}".getBytes();

        mvc.perform(post("/mef/filings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(request -> {
                            request.removeHeader(HttpHeaders.CONTENT_LENGTH);
                            request.addHeader(HttpHeaders.CONTENT_LENGTH,
                                    Long.toString(ClientFiling.MAX_CLIENT_BODY_CHARS + 1L));
                            return request;
                        }))
                .andExpect(status().isPayloadTooLarge());

        verify(desk, never()).file(any());
    }
}
