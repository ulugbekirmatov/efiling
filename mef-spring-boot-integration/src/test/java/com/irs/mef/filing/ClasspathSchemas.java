package com.irs.mef.filing;


import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

/** Test helper: the bundled root XSD the app validates a form against, not test-scenarios/schemas/941. */
final class ClasspathSchemas {

    private ClasspathSchemas() {}

    static File root(FormSpec form) {
        if (!(form.schema() instanceof SchemaBinding.Bundled bundled)) {
            throw new IllegalStateException("No schema bundled for " + form.code());
        }
        String resource = bundled.release().resolve(bundled.pathInBundle());
        URL url = ClasspathSchemas.class.getClassLoader().getResource(resource);
        if (url == null) {
            throw new IllegalStateException("Bundled schema missing from classpath: " + resource);
        }
        try {
            return new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Bad schema URL " + url, e);
        }
    }

    /** True once the release's IRS bundle has been copied onto the classpath, whatever its layout. */
    static boolean bundled(SchemaRelease release) {
        return ClasspathSchemas.class.getClassLoader().getResource(release.resolve("")) != null;
    }
}
