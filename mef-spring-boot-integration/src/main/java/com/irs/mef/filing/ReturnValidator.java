package com.irs.mef.filing;


/** Pre-flight check of the caller's return document. Cheaper than an IRS reject plus a 2-5 minute wait. */
interface ReturnValidator {

    /**
     * Always parses for well-formedness with DTDs and external entities disabled, then validates
     * against the form's bundled XSD. A pending form is refused with its reason.
     *
     * @throws FilingValidationException when the document is rejected
     */
    void validate(FormSpec form, String returnXml);
}
