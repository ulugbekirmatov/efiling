package com.irs.mef.ats;

import com.irs.mef.filing.IrsSession;
import com.irs.mef.live.LiveIrsTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;

/** Login probe for ./mef login. No send. */
@SpringBootTest
@LiveIrsTest
class IrsLoginProbeTest {

    static {
        String existing = System.getProperty("A2A_TOOLKIT_HOME");
        if (existing == null || existing.isBlank()) {
            System.setProperty(
                    "A2A_TOOLKIT_HOME",
                    Path.of(System.getProperty("user.dir"), "src/main/resources/mef_config")
                            .toAbsolutePath()
                            .toString());
        }
    }

    @Autowired
    IrsSession session;

    @Test
    void probesThenLogsOut() {
        session.probe();
        session.logout();
    }
}
