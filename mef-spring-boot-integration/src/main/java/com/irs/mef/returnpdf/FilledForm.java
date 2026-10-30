package com.irs.mef.returnpdf;

import java.util.Map;

/** One IRS form, the classpath PDF revision it is printed on, and the values for its fields. */
record FilledForm(IrsForm form, String templateResource, Map<String, String> values) {}
