package com.irs.mef.inspector.ring;

public sealed interface SoapCapture {

    record Present(CapturedXml xml) implements SoapCapture {
        public Present {
            if (xml == null) {
                throw new IllegalArgumentException("present SOAP capture requires xml");
            }
        }
    }

    record Missing(String reason) implements SoapCapture {
        public Missing {
            if (reason == null || reason.isBlank()) {
                reason = "SOAP part was not captured";
            }
        }
    }
}
