package com.irs.mef.returnpdf;

import java.util.Map;

record FilledForm(IrsForm form, String templateResource, Map<String, String> values) {}
