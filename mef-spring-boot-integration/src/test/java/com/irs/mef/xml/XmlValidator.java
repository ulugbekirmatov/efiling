package com.irs.mef.xml;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for validating XML files against XSD schemas.
 * Uses javax.xml.validation for standards-compliant XSD validation.
 */
@Slf4j
public class XmlValidator {

    /**
     * Validate an XML file against one or more XSD schema files.
     *
     * @param xmlFile The XML file to validate
     * @param xsdFiles One or more XSD schema files
     * @return ValidationResult containing validation status and any errors
     */
    public static ValidationResult validate(File xmlFile, File... xsdFiles) {
        log.info("Validating XML file: {} against {} schema files", xmlFile.getName(), xsdFiles.length);

        List<ValidationError> errors = new ArrayList<>();
        boolean isValid = true;

        try {
            // Create schema factory
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

            // Load all XSD files
            StreamSource[] sources = new StreamSource[xsdFiles.length];
            for (int i = 0; i < xsdFiles.length; i++) {
                sources[i] = new StreamSource(xsdFiles[i]);
                log.debug("Loading schema: {}", xsdFiles[i].getName());
            }

            // Compile schemas
            Schema schema = schemaFactory.newSchema(sources);

            // Create validator
            Validator validator = schema.newValidator();

            // Set custom error handler to collect all validation errors
            ValidationErrorHandler errorHandler = new ValidationErrorHandler(errors);
            validator.setErrorHandler(errorHandler);

            // Validate the XML file
            validator.validate(new StreamSource(xmlFile));

            // Check if any errors were collected
            if (!errors.isEmpty()) {
                isValid = false;
                log.warn("XML validation failed with {} error(s)", errors.size());
            } else {
                log.info("XML validation successful: {}", xmlFile.getName());
            }

        } catch (SAXException e) {
            log.error("Schema parsing error", e);
            errors.add(ValidationError.builder()
                    .type("FATAL")
                    .message("Schema parsing error: " + e.getMessage())
                    .build());
            isValid = false;
        } catch (Exception e) {
            log.error("Validation error", e);
            errors.add(ValidationError.builder()
                    .type("FATAL")
                    .message("Validation error: " + e.getMessage())
                    .build());
            isValid = false;
        }

        return ValidationResult.builder()
                .valid(isValid)
                .xmlFile(xmlFile.getName())
                .errorCount(errors.size())
                .errors(errors)
                .build();
    }

    /**
     * Generate a human-readable validation report.
     *
     * @param result The validation result
     * @return Formatted report string
     */
    public static String generateReport(ValidationResult result) {
        StringBuilder report = new StringBuilder();
        report.append("\n");
        report.append("========================================\n");
        report.append("XML Validation Report\n");
        report.append("========================================\n");
        report.append("File: ").append(result.getXmlFile()).append("\n");
        report.append("Status: ").append(result.isValid() ? "VALID" : "INVALID").append("\n");
        report.append("Error Count: ").append(result.getErrorCount()).append("\n");
        report.append("========================================\n");

        if (!result.isValid() && !result.getErrors().isEmpty()) {
            report.append("\nValidation Errors:\n");
            report.append("----------------------------------------\n");
            int errorNum = 1;
            for (ValidationError error : result.getErrors()) {
                report.append(errorNum++).append(". ");
                report.append("[").append(error.getType()).append("] ");
                if (error.getLineNumber() > 0) {
                    report.append("Line ").append(error.getLineNumber());
                    if (error.getColumnNumber() > 0) {
                        report.append(", Column ").append(error.getColumnNumber());
                    }
                    report.append(": ");
                }
                report.append(error.getMessage()).append("\n");
            }
            report.append("========================================\n");
        }

        return report.toString();
    }

    /**
     * Custom error handler that collects validation errors.
     */
    private static class ValidationErrorHandler implements ErrorHandler {
        private final List<ValidationError> errors;

        public ValidationErrorHandler(List<ValidationError> errors) {
            this.errors = errors;
        }

        @Override
        public void warning(SAXParseException exception) {
            errors.add(ValidationError.builder()
                    .type("WARNING")
                    .lineNumber(exception.getLineNumber())
                    .columnNumber(exception.getColumnNumber())
                    .message(exception.getMessage())
                    .build());
        }

        @Override
        public void error(SAXParseException exception) {
            errors.add(ValidationError.builder()
                    .type("ERROR")
                    .lineNumber(exception.getLineNumber())
                    .columnNumber(exception.getColumnNumber())
                    .message(exception.getMessage())
                    .build());
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            errors.add(ValidationError.builder()
                    .type("FATAL")
                    .lineNumber(exception.getLineNumber())
                    .columnNumber(exception.getColumnNumber())
                    .message(exception.getMessage())
                    .build());
            throw exception; // Re-throw fatal errors
        }
    }

    /**
     * Validation result object containing validation status and errors.
     */
    @Data
    @Builder
    public static class ValidationResult {
        private boolean valid;
        private String xmlFile;
        private int errorCount;
        private List<ValidationError> errors;
    }

    /**
     * Individual validation error details.
     */
    @Data
    @Builder
    public static class ValidationError {
        private String type; // WARNING, ERROR, FATAL
        private int lineNumber;
        private int columnNumber;
        private String message;
    }
}
