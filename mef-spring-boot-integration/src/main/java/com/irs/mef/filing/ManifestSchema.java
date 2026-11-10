package com.irs.mef.filing;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.StringReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class ManifestSchema {

    static final String MISSING =
            "no manifest schema in the repo or the SDK jar; X0000-008 LocalRule checks the efile namespace";

    private static final String RESOURCE = "META-INF/wsdl/efileAttachments.xsd";
    private static final int MAX_REPORTED_ERRORS = 5;

    private ManifestSchema() {}

    static String validate(String manifestXml) {
        URL schemaUrl = locate();
        if (schemaUrl == null) {
            return MISSING;
        }
        List<String> errors = errors(schemaUrl, manifestXml);
        if (errors.isEmpty()) {
            return "manifest schema: " + schemaUrl;
        }
        String summary = String.join("; ", errors.subList(0, Math.min(errors.size(), MAX_REPORTED_ERRORS)));
        throw new FilingValidationException("manifestXml",
                "Manifest XML failed schema validation: " + summary);
    }

    private static URL locate() {
        URL classpath = ManifestSchema.class.getClassLoader().getResource(RESOURCE);
        if (classpath != null) {
            return classpath;
        }
        Path cwd = Path.of(System.getProperty("user.dir"));
        for (Path candidate : List.of(
                cwd.resolve("sdk-reference/extracted").resolve(RESOURCE),
                cwd.resolve("mef-spring-boot-integration/sdk-reference/extracted").resolve(RESOURCE))) {
            if (Files.isRegularFile(candidate)) {
                try {
                    return candidate.toUri().toURL();
                } catch (java.net.MalformedURLException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private static List<String> errors(URL schemaUrl, String manifestXml) {
        Schema schema;
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            try {
                factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            } catch (SAXException ignored) {
            }
            schema = factory.newSchema(schemaUrl);
        } catch (SAXException e) {
            throw new IllegalStateException("Cannot compile manifest schema " + schemaUrl, e);
        }
        List<String> errors = new ArrayList<>();
        try {
            Validator validator = schema.newValidator();
            try {
                validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            } catch (SAXException ignored) {
            }
            validator.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) {
                }

                @Override
                public void error(SAXParseException exception) {
                    errors.add("line " + exception.getLineNumber() + ": " + exception.getMessage());
                }

                @Override
                public void fatalError(SAXParseException exception) {
                    errors.add("line " + exception.getLineNumber() + ": " + exception.getMessage());
                }
            });
            validator.validate(new StreamSource(new StringReader(manifestXml)));
        } catch (SAXException | java.io.IOException e) {
            if (errors.isEmpty()) {
                errors.add(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        }
        return errors;
    }
}
