package com.irs.mef.ats;

import com.irs.mef.filing.FilingOutcome;
import com.irs.mef.filing.IrsFault;
import com.irs.mef.filing.RuleResult;
import com.irs.mef.filing.SubmissionId;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AtsVerdictTest {

    @Test
    void onlyAcceptedExitsZero() {
        SubmissionId id = new SubmissionId("0000002026286abc1234");
        List<AtsVerdict> verdicts = List.of(
                new AtsVerdict.Accepted(id),
                new AtsVerdict.Rejected(id, Set.of("X0000-008")),
                new AtsVerdict.NoAck(id),
                new AtsVerdict.NotSent(new FilingOutcome.Indeterminate(new IrsFault(
                        "NEWSEND_ENVIRONMENT", "refused", null, null, null))),
                new AtsVerdict.Blocked(new RejectLedger.Blocked(id, Set.of("R9999-000"))),
                new AtsVerdict.PreflightFailed(List.of(new RuleResult("R0000-118-01", false, "prefix"))));

        EnumSet<AtsVerdict.Kind> kinds = EnumSet.noneOf(AtsVerdict.Kind.class);
        for (AtsVerdict verdict : verdicts) {
            kinds.add(verdict.kind());
            if (verdict instanceof AtsVerdict.Accepted) {
                assertEquals(0, verdict.exitCode());
            } else {
                assertNotEquals(0, verdict.exitCode());
            }
        }
        assertEquals(EnumSet.allOf(AtsVerdict.Kind.class), kinds);
    }
}
