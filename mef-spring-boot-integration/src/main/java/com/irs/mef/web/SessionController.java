package com.irs.mef.web;

import com.irs.mef.filing.CertificateDiagnostic;
import com.irs.mef.filing.IrsSession;
import com.irs.mef.filing.SessionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mef/session")
@Slf4j
public class SessionController {

    private final IrsSession session;

    public SessionController(IrsSession session) {
        this.session = session;
    }

    @PostMapping("/login")
    public SessionStatus login() {
        log.info("Session login requested");
        return session.probe();
    }

    @PostMapping("/logout")
    public SessionStatus logout() {
        boolean hadSession = session.status().loggedIn();
        log.info("Session logout requested (hadSession={})", hadSession);
        session.logout();
        if (!hadSession) {
            return new SessionStatus(false, null, "No active session");
        }
        return new SessionStatus(false, null, "Logged out");
    }

    /** Local status only. This GET must not contact IRS. */
    @GetMapping
    public SessionStatus status() {
        return session.status();
    }

    /** Offline keystore diagnostic. This GET must not contact IRS. */
    @GetMapping("/certificate")
    public CertificateDiagnostic certificate() {
        return session.certificate();
    }
}
