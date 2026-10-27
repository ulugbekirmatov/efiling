package com.irs.mef.filing;

/**
 * Package-private so a test can stub login without the SDK. The real implementation is
 * {@link ReflectiveSessionLogin}.
 */
interface SessionLogin {

    Opened open();

    record Opened(Object serviceContext, String sessionId) {}
}
