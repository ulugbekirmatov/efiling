package com.irs.mef.inspector.capture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrettyXmlTest {

    @Test
    @DisplayName("unparseable XML is returned unchanged")
    void parseFailureReturnsOriginal() {
        String broken = "<Return><unterminated";
        assertEquals(broken, PrettyXml.indent(broken));
    }
}
