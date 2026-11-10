package com.irs.mef.wagefile;

/** One tax year of employers and employees. The live payroll query is not this type. */
public interface WageYearSource {

    WageYear load(int taxYear);
}
