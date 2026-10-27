package com.irs.mef.filing;

/** One IRS reject reproduced offline. The sample is the bytes this rule must fail on. */
interface LocalRule {

    String irsCode();

    RuleResult check(Preflight preflight);

    /** Classpath directory holding the scrubbed return and manifest. */
    String rejectedSample();
}
