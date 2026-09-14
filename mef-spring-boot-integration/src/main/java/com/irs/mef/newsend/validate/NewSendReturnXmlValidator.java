package com.irs.mef.newsend.validate;

import com.irs.mef.newsend.domain.FormType;

/** Pre-flight check of the caller's return document. Cheaper than an IRS reject plus a 2-5 minute wait. */
public interface NewSendReturnXmlValidator {

    /**
     * Always parses for well-formedness with DTDs and external entities disabled — the body is
     * untrusted input. Schema validation additionally applies per {@code mef.newsend.xml-validation}:
     * ENFORCE throws on schema errors (400), WARN logs and proceeds, OFF checks well-formedness only.
     * A form with no bundled XSD degrades to WARN regardless of mode.
     *
     * @throws com.irs.mef.newsend.error.NewSendValidationException when the document is rejected
     */
    void validate(FormType formType, String returnXml);
}
