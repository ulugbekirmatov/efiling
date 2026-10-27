package com.irs.mef.ats;

import com.irs.mef.filing.FilingOutcome;
import com.irs.mef.filing.RuleResult;
import com.irs.mef.filing.SubmissionId;

import java.util.List;
import java.util.Set;

public sealed interface AtsVerdict {

    enum Kind {
        ACCEPTED, REJECTED, NO_ACK, NOT_SENT, BLOCKED, PREFLIGHT_FAILED
    }

    Kind kind();

    default int exitCode() {
        return switch (kind()) {
            case ACCEPTED -> 0;
            case REJECTED -> 1;
            case NO_ACK -> 2;
            case NOT_SENT -> 4;
            case BLOCKED -> 5;
            case PREFLIGHT_FAILED -> 6;
        };
    }

    record Accepted(SubmissionId id) implements AtsVerdict {
        @Override
        public Kind kind() {
            return Kind.ACCEPTED;
        }
    }

    record Rejected(SubmissionId id, Set<String> codes) implements AtsVerdict {
        public Rejected {
            codes = Set.copyOf(codes);
        }

        @Override
        public Kind kind() {
            return Kind.REJECTED;
        }
    }

    record NoAck(SubmissionId id) implements AtsVerdict {
        @Override
        public Kind kind() {
            return Kind.NO_ACK;
        }
    }

    record NotSent(FilingOutcome why) implements AtsVerdict {
        @Override
        public Kind kind() {
            return Kind.NOT_SENT;
        }
    }

    record Blocked(RejectLedger.Blocked why) implements AtsVerdict {
        @Override
        public Kind kind() {
            return Kind.BLOCKED;
        }
    }

    record PreflightFailed(List<RuleResult> failures) implements AtsVerdict {
        public PreflightFailed {
            failures = List.copyOf(failures);
        }

        @Override
        public Kind kind() {
            return Kind.PREFLIGHT_FAILED;
        }
    }
}
