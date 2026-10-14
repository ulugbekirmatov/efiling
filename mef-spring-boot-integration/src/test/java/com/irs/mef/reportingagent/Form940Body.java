package com.irs.mef.reportingagent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** A minimal annual 940 client body for offline tests; the real annual ATS fixtures live elsewhere. */
final class Form940Body {

    static final String ATTRIBUTES = "returnTypeCd=\"940\" returnVersion=\"2026v2.0\""
            + " taxPeriodBegin=\"2026-01-01\" taxPeriodEnd=\"2026-12-31\" taxYr=\"2026\"";

    private Form940Body() {}

    /** Writes the body with {@link #ATTRIBUTES}, after replacing {@code from} with {@code to}. */
    static Path write(Path dir, String from, String to) throws IOException {
        if (!ATTRIBUTES.contains(from)) {
            throw new IllegalStateException("940 body attributes do not contain " + from);
        }
        return Files.writeString(dir.resolve("client-return-body-940.xml"), xml(ATTRIBUTES.replace(from, to)));
    }

    static Path write(Path dir) throws IOException {
        return Files.writeString(dir.resolve("client-return-body-940.xml"), xml(ATTRIBUTES));
    }

    private static String xml(String attributes) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <ClientReturnBody xmlns="http://www.irs.gov/efile" %s>
                  <Filer>
                    <EIN>003000004</EIN>
                    <BusinessName>
                      <BusinessNameLine1Txt>Orchid Incorporated</BusinessNameLine1Txt>
                    </BusinessName>
                    <BusinessNameControlTxt>ORCH</BusinessNameControlTxt>
                    <USAddress>
                      <AddressLine1Txt>1st Test Street</AddressLine1Txt>
                      <CityNm>Willow Grove</CityNm>
                      <StateAbbreviationCd>PA</StateAbbreviationCd>
                      <ZIPCd>19090</ZIPCd>
                    </USAddress>
                  </Filer>
                  <ReturnData documentCnt="1">
                    <IRS940 documentId="IRS940"/>
                  </ReturnData>
                </ClientReturnBody>
                """.formatted(attributes);
    }
}
