package com.irs.mef.reportingagent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Offline. The returnTypeCd guards, driven by edits of the scenario 1 body. */
class ClientReturnBodyTest {

    private static final String TYPE_941 = "returnTypeCd=\"941\"";

    @TempDir
    Path dir;

    @Test
    void scenario1BodyLoadsAs941() {
        ClientReturnBody body = ClientReturnBody.load(AtsScenario.ORCHID_941.clientBody());
        assertEquals(ClientReturnType.F941, body.returnType());
        assertEquals("003000004", body.filerEin().value());
    }

    @Test
    void rejects941XLabelOnA941ReturnData() throws IOException {
        Path mislabeled = edited(TYPE_941, "returnTypeCd=\"941X\"");
        ReportingAgentValidationException ex = assertThrows(
                ReportingAgentValidationException.class, () -> ClientReturnBody.load(mislabeled));
        assertEquals("returnTypeCd", ex.field());
        assertEquals("returnTypeCd 941X requires ReturnData to open with IRS941X, found IRS941", ex.getMessage());
    }

    @Test
    void rejectsReturnTypeOutsideTheRegistry() throws IOException {
        Path unsupported = edited(TYPE_941, "returnTypeCd=\"943\"");
        ReportingAgentValidationException ex = assertThrows(
                ReportingAgentValidationException.class, () -> ClientReturnBody.load(unsupported));
        assertEquals("returnTypeCd", ex.field());
        assertEquals("Unknown returnTypeCd '943'. Must be one of: 941, 941X", ex.getMessage());
    }

    @Test
    void requiresReturnTypeCd() throws IOException {
        Path untyped = edited(TYPE_941, "");
        ReportingAgentValidationException ex = assertThrows(
                ReportingAgentValidationException.class, () -> ClientReturnBody.load(untyped));
        assertEquals("returnTypeCd", ex.field());
        assertEquals("returnTypeCd is required", ex.getMessage());
    }

    @Test
    void rejectsTheRetiredClient941BodyRoot() throws IOException {
        Path retired = Files.writeString(dir.resolve("retired.xml"),
                scenario1().replace("ClientReturnBody", "Client941Body"));
        ReportingAgentValidationException ex = assertThrows(
                ReportingAgentValidationException.class, () -> ClientReturnBody.load(retired));
        assertEquals("xml", ex.field());
    }

    private Path edited(String from, String to) throws IOException {
        String source = scenario1();
        if (!source.contains(from)) {
            throw new IllegalStateException("scenario 1 fixture no longer contains " + from);
        }
        return Files.writeString(dir.resolve("edited.xml"), source.replace(from, to));
    }

    private static String scenario1() throws IOException {
        return Files.readString(AtsScenario.ORCHID_941.clientBody());
    }
}
