package com.irs.mef.filing;

public record SessionStatus(boolean loggedIn, String sessionId, String message) {}
