package com.irs.mef.filing;

interface SessionLogin {

    Opened open();

    record Opened(Object serviceContext, String sessionId) {}
}
