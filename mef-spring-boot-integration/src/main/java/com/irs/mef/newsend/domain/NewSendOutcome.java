package com.irs.mef.newsend.domain;

/**
 * What a SendSubmissions call can be worth. THREE cases, not two.
 *
 * The third one is the whole point: if invoke() throws anything that is not a recognised
 * IRS business fault, the container MAY have reached the IRS. Calling that "failed" is how
 * duplicate filings get created — so the type simply does not have a "failed after send" case.
 */
public sealed interface NewSendOutcome {

    NewSendState state();

    /** IRS holds the filing; the receipt confirms TRANSMISSION, not acceptance. */
    record Transmitted(NewSendReceipt receipt) implements NewSendOutcome {
        @Override
        public NewSendState state() {
            return NewSendState.TRANSMITTED;
        }
    }

    /** IRS explicitly refused the container. Nothing was filed. A corrected re-file needs a NEW key. */
    record Rejected(NewSendFault fault) implements NewSendOutcome {
        @Override
        public NewSendState state() {
            return NewSendState.REJECTED;
        }
    }

    /** Unknown. Do NOT resubmit; reconcile via GetSubmissionStatus / acks on the submission id. */
    record Indeterminate(NewSendFault fault) implements NewSendOutcome {
        @Override
        public NewSendState state() {
            return NewSendState.INDETERMINATE;
        }
    }
}
