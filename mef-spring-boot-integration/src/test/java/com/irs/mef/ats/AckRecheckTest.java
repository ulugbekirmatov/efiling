package com.irs.mef.ats;

import com.irs.mef.ack.StoredAck;
import com.irs.mef.filing.IrsSession;
import com.irs.mef.live.LiveIrsTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Re-polls GetAck for submissions that already have a stored ack. The ack store is not touched;
 * the live answer is printed next to the stored one so the two can be compared by eye.
 * Armed with -Dmef.live.test=AckRecheckTest -Dmef.ack.ids=id1,id2,...
 */
@SpringBootTest
@LiveIrsTest
class AckRecheckTest {

    static final String IDS_PROPERTY = "mef.ack.ids";

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
    void repollsEveryRequestedAck() {
        List<String> ids = Arrays.stream(System.getProperty(IDS_PROPERTY, "").split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .toList();
        assertTrue(!ids.isEmpty(), "pass -D" + IDS_PROPERTY + "=id1,id2,...");
        List<String> notAccepted = new ArrayList<>();
        try {
            for (String id : ids) {
                Optional<StoredAck> live = session.getAck(id);
                if (live.isEmpty()) {
                    System.out.printf("ACK-RECHECK %s  NO ACK RETURNED%n", id);
                    notAccepted.add(id);
                    continue;
                }
                StoredAck ack = live.get();
                System.out.printf("ACK-RECHECK %s  %s  ackId=%s  form=%s  taxYear=%s  ein=%s  postmark=%s  errors=%s%n",
                        id, ack.ackType(), ack.ackId(), ack.submissionType(), ack.taxYear(),
                        ack.ein(), ack.electronicPostmarkTs(), ack.errorCodes());
                if (!"Accepted".equals(ack.ackType())) {
                    notAccepted.add(id);
                }
            }
        } finally {
            session.logout();
        }
        assertTrue(notAccepted.isEmpty(), "not Accepted on re-poll: " + notAccepted);
    }
}
