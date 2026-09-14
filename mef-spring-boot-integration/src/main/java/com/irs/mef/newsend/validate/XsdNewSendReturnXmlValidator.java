package com.irs.mef.newsend.validate;

import com.irs.mef.newsend.NewSendProperties;
import com.irs.mef.newsend.domain.FormType;
import com.irs.mef.newsend.error.NewSendValidationException;
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
 * Schemas are compiled once per form and cached; the 941 set is bundled under
 * classpath:schemas/94x/941/ (copied from the IRS 2026 schema drop).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class XsdNewSendReturnXmlValidator implements NewSendReturnXmlValidator {

    private static final int MAX_REPORTED_ERRORS = 5;

    private final NewSendProperties properties;
    private final Map<FormType, Schema> schemaCache = new ConcurrentHashMap<>();

    @Override
    public void validate(FormType formType, String returnXml) {
        assertWellFormed(returnXml);
        NewSendProperties.XmlValidationMode mode = properties.getXmlValidation();
        if (mode == NewSendProperties.XmlValidationMode.OFF) {
            return;
        }
        Optional<String> schemaRoot = formType.schemaRoot();
        if (schemaRoot.isEmpty()) {
            log.warn("No XSD bundled for form type {} — schema validation skipped", formType.code());
            return;
        }
        List<String> errors = schemaErrors(formType, schemaRoot.get(), returnXml);
        if (errors.isEmpty()) {
            return;
        }
        String summary = String.join("; ", errors.subList(0, Math.min(errors.size(), MAX_REPORTED_ERRORS)));
        if (mode == NewSendProperties.XmlValidationMode.ENFORCE) {
            throw new NewSendValidationException("returnXml",
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
            throw new NewSendValidationException("returnXml",
                    "Return XML is not well-formed (line " + e.getLineNumber() + "): " + e.getMessage());
        } catch (Exception e) {
            throw new NewSendValidationException("returnXml", "Return XML could not be parsed: " + e.getMessage());
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

    private Schema loadSchema(String schemaRoot) {
        URL schemaUrl = getClass().getClassLoader().getResource(schemaRoot);
        if (schemaUrl == null) {
            throw new IllegalStateException("Bundled schema missing from classpath: " + schemaRoot);
        }
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
