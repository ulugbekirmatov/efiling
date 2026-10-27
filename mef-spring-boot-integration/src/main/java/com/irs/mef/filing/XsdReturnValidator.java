package com.irs.mef.filing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * XXE-hardened well-formedness check plus optional per-form XSD validation.
 * Schemas are compiled once per form and cached; each root sits in its form's IRS schema
 * release bundle on the classpath (see {@link FormType#schemaRoot()}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
class XsdReturnValidator implements ReturnValidator {

    private static final int MAX_REPORTED_ERRORS = 5;

    private final FilingProperties properties;
    private final Map<FormType, Schema> schemaCache = new ConcurrentHashMap<>();

    @Override
    public void validate(FormType formType, String returnXml) {
        assertWellFormed(returnXml);
        Optional<String> schemaRoot = formType.schemaRoot();
        // Checked before the OFF switch: a form tied to a schema release never goes out while that release is absent.
        schemaRoot.ifPresent(this::requireBundled);
        FilingProperties.XmlValidationMode mode = properties.getXmlValidation();
        if (mode == FilingProperties.XmlValidationMode.OFF) {
            return;
        }
        if (schemaRoot.isEmpty()) {
            log.warn("No XSD bundled for form type {} — schema validation skipped", formType.code());
            return;
        }
        List<String> errors = schemaErrors(formType, schemaRoot.get(), returnXml);
        if (errors.isEmpty()) {
            return;
        }
        String summary = String.join("; ", errors.subList(0, Math.min(errors.size(), MAX_REPORTED_ERRORS)));
        if (mode == FilingProperties.XmlValidationMode.ENFORCE) {
            throw new FilingValidationException("returnXml",
                    "Return XML failed " + formType.code() + " schema validation: " + summary);
        }
        log.warn("Return XML has {} schema violation(s) for form {} (mode=WARN, proceeding): {}",
                errors.size(), formType.code(), summary);
    }

    private void assertWellFormed(String returnXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.newDocumentBuilder().parse(new InputSource(new StringReader(returnXml)));
        } catch (SAXParseException e) {
            throw new FilingValidationException("returnXml",
                    "Return XML is not well-formed (line " + e.getLineNumber() + "): " + e.getMessage());
        } catch (Exception e) {
            throw new FilingValidationException("returnXml", "Return XML could not be parsed: " + e.getMessage());
        }
    }

    private List<String> schemaErrors(FormType formType, String schemaRoot, String returnXml) {
        Schema schema = schemaCache.computeIfAbsent(formType, ignored -> loadSchema(schemaRoot));
        List<String> errors = new ArrayList<>();
        try {
            Validator validator = schema.newValidator();
            hardenQuietly(validator);
            validator.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException e) {
                    // schema warnings are noise for this purpose
                }

                @Override
                public void error(SAXParseException e) {
                    errors.add("line " + e.getLineNumber() + ": " + e.getMessage());
                }

                @Override
                public void fatalError(SAXParseException e) throws SAXException {
                    throw e;
                }
            });
            validator.validate(new StreamSource(new StringReader(returnXml)));
        } catch (SAXException | java.io.IOException e) {
            errors.add(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
        return errors;
    }

    private void requireBundled(String schemaRoot) {
        if (getClass().getClassLoader().getResource(schemaRoot) == null) {
            throw new FilingConfigurationException("Bundled schema missing from classpath: " + schemaRoot);
        }
    }

    private Schema loadSchema(String schemaRoot) {
        URL schemaUrl = getClass().getClassLoader().getResource(schemaRoot);
        try {
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            trySetProperty(schemaFactory, XMLConstants.ACCESS_EXTERNAL_DTD);
            return schemaFactory.newSchema(schemaUrl);
        } catch (SAXException e) {
            throw new IllegalStateException("Cannot compile schema " + schemaRoot, e);
        }
    }

    private void hardenQuietly(Validator validator) {
        try {
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (SAXException e) {
            log.debug("Validator does not support external-access properties: {}", e.getMessage());
        }
    }

    private void trySetProperty(SchemaFactory schemaFactory, String property) {
        try {
            schemaFactory.setProperty(property, "");
        } catch (SAXException e) {
            log.debug("SchemaFactory does not support {}: {}", property, e.getMessage());
        }
    }
}
