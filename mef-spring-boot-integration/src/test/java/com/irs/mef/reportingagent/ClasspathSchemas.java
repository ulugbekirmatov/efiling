package com.irs.mef.reportingagent;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/** Test helper → src/main/resources/schemas/94x/941/Return941.xsd — not test-scenarios/schemas/941. */
final class ClasspathSchemas {

    private ClasspathSchemas() {}

    static File return941() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path module = cwd.endsWith("mef-spring-boot-integration")
                ? cwd
                : cwd.resolve("mef-spring-boot-integration");
        Path schema = module.resolve("src/main/resources/schemas/94x/941/Return941.xsd");
        if (!Files.exists(schema)) {
            throw new IllegalStateException("Return941.xsd not found at " + schema);
        }
        return schema.toFile();
    }
}
