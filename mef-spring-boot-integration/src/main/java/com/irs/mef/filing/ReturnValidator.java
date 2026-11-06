package com.irs.mef.filing;


interface ReturnValidator {

    void validate(FormSpec form, SchemaBinding schema, String returnXml);
}
