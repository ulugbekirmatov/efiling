package com.irs.mef.filing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** The Zinnia 940 ATS fixture, optionally with one root-attribute edit, for offline negative tests. */
final class Form940Body {

    private Form940Body() {}

    static Path write(Path dir) throws IOException {
        return Files.copy(AtsScenario.ZINNIA_940.clientBody(), dir.resolve("client-return-body-940.xml"));
    }

    /** Writes the fixture after replacing {@code from} with {@code to}; {@code from} must occur in it. */
    static Path write(Path dir, String from, String to) throws IOException {
        String source = Files.readString(AtsScenario.ZINNIA_940.clientBody());
        if (!source.contains(from)) {
            throw new IllegalStateException("Zinnia 940 fixture does not contain " + from);
        }
        return Files.writeString(dir.resolve("client-return-body-940.xml"), source.replace(from, to));
    }
}
