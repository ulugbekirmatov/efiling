package com.irs.mef.filing;

import java.time.ZoneId;

/** The e-file namespace and the processing zone, each in one place. */
final class Efile {

    static final String NAMESPACE = "http://www.irs.gov/efile";

    /** IRS processing dates are Eastern. The submission id's yyyyDDD uses this zone. */
    static final ZoneId PROCESSING_ZONE = ZoneId.of("America/New_York");

    private Efile() {}
}
