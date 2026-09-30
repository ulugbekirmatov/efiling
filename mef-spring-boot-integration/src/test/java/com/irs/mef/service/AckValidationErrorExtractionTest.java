package com.irs.mef.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irs.mef.ack.FileAckStore;
import com.irs.mef.config.MefSdkConfig;
import com.irs.mef.dto.AckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.retry.support.RetryTemplate;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AckValidationErrorExtractionTest {

    private static final Instant NOW = Instant.parse("2026-09-25T09:26:34Z");
    private static final String MSG_244 = "The IP Address ... must have a valid value.";
    private static final String MSG_248 = "If Form 94x, Refund Product Election Indicator must have a valid value.";

    @TempDir
    Path temp;

    private MefClientService mefClientService;
    private RetryTemplate retryTemplate;

    @BeforeEach
    void setUp() {
        mefClientService = mock(MefClientService.class);
        retryTemplate = mock(RetryTemplate.class);
    }

    @Test
    @DisplayName("rejected ack with two validation errors maps codes, messages, and details")
    void rejectedAckExtractsTwoValidationErrors() throws Exception {
        FakeAck ack = new FakeAck("Rejected", new FakeValidationErrorList(List.of(
                new FakeValidationError("R0000-244", "Reject and Stop", MSG_244, null, ""),
                new FakeValidationError("R0000-248", "Reject and Stop", MSG_248, null, "X"))));

        AckResponse response = invokeBuild(service(), ack);

        assertEquals("Rejected", response.getAckType());
        assertEquals(List.of("R0000-244", "R0000-248"), response.getErrorCodes());
        assertEquals(List.of(
                "[Reject and Stop] " + MSG_244,
                "[Reject and Stop] " + MSG_248 + " [Value: X]"), response.getErrorMessages());
        assertEquals(true, response.getHasValidationErrors());
        assertTrue(response.getDetails().startsWith("Validation errors (2):"));
    }

    @Test
    @DisplayName("accepted ack with a null validation error list has no errors")
    void acceptedAckWithNullErrorListHasNoErrors() throws Exception {
        FakeAck ack = new FakeAck("Accepted", null);

        AckResponse response = invokeBuild(service(), ack);

        assertEquals("Accepted", response.getAckType());
        assertEquals(List.of(), response.getErrorCodes());
        assertEquals(List.of(), response.getErrorMessages());
        assertFalse(response.getHasValidationErrors());
        assertEquals("Acknowledgment retrieved successfully", response.getDetails());
    }

    private AcknowledgementService service() {
        return new AcknowledgementService(mefClientService, new MefSdkConfig(), retryTemplate,
                new FileAckStore(temp, new ObjectMapper()), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AckResponse invokeBuild(AcknowledgementService service, Object ack) throws Exception {
        Method method = AcknowledgementService.class.getDeclaredMethod("buildAckResponseReflection", Object.class);
        method.setAccessible(true);
        return (AckResponse) method.invoke(service, ack);
    }

    public static class FakeAck {
        private final String acceptanceStatusTxt;
        private final FakeValidationErrorList validationErrorList;

        FakeAck(String acceptanceStatusTxt, FakeValidationErrorList validationErrorList) {
            this.acceptanceStatusTxt = acceptanceStatusTxt;
            this.validationErrorList = validationErrorList;
        }

        public String getSubmissionId() {
            return null;
        }

        public String getAcceptanceStatusTxt() {
            return acceptanceStatusTxt;
        }

        public FakeValidationErrorList getValidationErrorList() {
            return validationErrorList;
        }
    }

    public static class FakeValidationErrorList {
        private final List<FakeValidationError> errors;

        FakeValidationErrorList(List<FakeValidationError> errors) {
            this.errors = errors;
        }

        public List<FakeValidationError> getValidationErrorGrp() {
            return errors;
        }

        public Integer getErrorCnt() {
            return errors.size();
        }
    }

    public static class FakeValidationError {
        private final String ruleNum;
        private final String severityCd;
        private final String errorMessageTxt;
        private final String xpathContentTxt;
        private final String fieldValueTxt;

        FakeValidationError(String ruleNum, String severityCd, String errorMessageTxt,
                String xpathContentTxt, String fieldValueTxt) {
            this.ruleNum = ruleNum;
            this.severityCd = severityCd;
            this.errorMessageTxt = errorMessageTxt;
            this.xpathContentTxt = xpathContentTxt;
            this.fieldValueTxt = fieldValueTxt;
        }

        public String getRuleNum() {
            return ruleNum;
        }

        public String getSeverityCd() {
            return severityCd;
        }

        public String getErrorMessageTxt() {
            return errorMessageTxt;
        }

        public String getXpathContentTxt() {
            return xpathContentTxt;
        }

        public String getFieldValueTxt() {
            return fieldValueTxt;
        }
    }
}
