package com.irs.mef.filing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

@SpringBootTest(properties = {
        "mef.newsend.journal.mode=memory",
        "mef.inspector.enabled=false",
        "mef.ack-store.path=${java.io.tmpdir}/mef-u4-acks"
})
@AutoConfigureMockMvc
@Import(GetRoutesNeverReachIrsTest.FailingLogin.class)
class GetRoutesNeverReachIrsTest {

    static final AtomicBoolean LOGIN_INVOKED = new AtomicBoolean();

    @Autowired
    MockMvc mvc;

    @Autowired
    RequestMappingHandlerMapping mappings;

    @Test
    void everyGetMappingLeavesTheLoginSeamAlone() throws Exception {
        LOGIN_INVOKED.set(false);
        List<String> paths = new ArrayList<>();
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            Set<org.springframework.web.bind.annotation.RequestMethod> methods =
                    info.getMethodsCondition().getMethods();
            if (!methods.isEmpty() && !methods.contains(GET)) {
                continue;
            }
            if (info.getPathPatternsCondition() == null) {
                continue;
            }
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                paths.add(pattern.replaceAll("\\{[^}]+}", "0000002026001abcdef"));
            }
        }

        assertTrue(paths.contains("/mef/session"), paths.toString());
        for (String path : paths) {
            mvc.perform(get(path));
        }
        mvc.perform(get("/mef/session")).andExpect(status().isOk());
        mvc.perform(get("/mef/session/certificate")).andExpect(status().isOk());
        assertFalse(LOGIN_INVOKED.get(), "a GET mapping invoked IRS login: " + paths);
    }

    @TestConfiguration
    static class FailingLogin {
        @Bean
        @Primary
        SessionLogin sessionLogin() {
            return () -> {
                LOGIN_INVOKED.set(true);
                throw new IllegalStateException("IRS login invoked");
            };
        }
    }
}
