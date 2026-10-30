package com.irs.mef.filing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AckValidationErrorExtractionTest {

    private static final Instant NOW = Instant.parse("2026-09-25T09:26:34Z");
    private static final String SUBMISSION_ID = "2378612026268yagqidh";
    private static final String MSG_244 = "The IP Address ... must have a valid value.";
    private static final String MSG_248 = "If Form 94x, Refund Product Election Indicator must have a valid value.";

    private final IrsWire wire = new IrsWire(Clock.fixed(NOW, ZoneOffset.UTC), NoOpSendSubmissionsWireTap.INSTANCE);

    @Test
    @DisplayName("rejected ack with two validation errors maps codes, messages, and details")
    void rejectedAckExtractsTwoValidationErrors() {
        FakeAck ack = new FakeAck("Rejected", new FakeValidationErrorList(List.of(
                new FakeValidationError("R0000-244", "Reject and Stop", MSG_244, null, ""),
                new FakeValidationError("R0000-248", "Reject and Stop", MSG_248, null, "X"))));

        var stored = wire.parse(ack, SUBMISSION_ID);

        assertEquals("Rejected", stored.ackType());
        assertEquals(List.of("R0000-244", "R0000-248"), stored.errorCodes());
        assertEquals(List.of(
                "[Reject and Stop] " + MSG_244,
                "[Reject and Stop] " + MSG_248 + " [Value: X]"), stored.errorMessages());
        assertEquals(true, stored.hasValidationErrors());
        assertTrue(stored.details().startsWith("Validation errors (2):"));
        assertEquals(SUBMISSION_ID, stored.submissionId());
        assertEquals(NOW, stored.retrievedAt());
    }

    @Test
    @DisplayName("accepted ack with a null validation error list has no errors")
    void acceptedAckWithNullErrorListHasNoErrors() {
        FakeAck ack = new FakeAck("Accepted", null);

        var stored = wire.parse(ack, SUBMISSION_ID);

        assertEquals("Accepted", stored.ackType());
        assertEquals(List.of(), stored.errorCodes());
        assertEquals(List.of(), stored.errorMessages());
        assertFalse(stored.hasValidationErrors());
        assertEquals("Acknowledgment retrieved successfully", stored.details());
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
