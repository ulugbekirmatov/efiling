package com.irs.mef.filing;

/** Local session view. Reading it does not contact IRS. */
public record SessionStatus(boolean loggedIn, String sessionId, String message) {}
