package com.irs.mef.filing;


import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

/** Test helper: the bundled root XSD the app validates a form against, not test-scenarios/schemas/941. */
final class ClasspathSchemas {

    private ClasspathSchemas() {}

    static File root(FormSpec form) {
        String resource = form.schema().resource();
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
}
