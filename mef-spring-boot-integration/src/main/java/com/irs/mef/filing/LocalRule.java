package com.irs.mef.filing;

interface LocalRule {

    String irsCode();

    RuleResult check(Preflight preflight);

    String rejectedSampleClasspathDir();
}
