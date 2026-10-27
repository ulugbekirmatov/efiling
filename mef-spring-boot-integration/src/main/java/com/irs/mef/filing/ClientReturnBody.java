package com.irs.mef.filing;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/**
 * Employer return body for one client. Owns the return type, filer EIN, tax period, reporting
 * period (quarter or tax year), and the opaque ReturnData XML (the main form plus any schedules).
 * Does not contain OriginatorGrp, any PIN group, SoftwareId, or ReportingAgent94XFilerGrp.
 */
final class ClientReturnBody {

    static final String NS = "http://www.irs.gov/efile";

    private final ClientReturnType returnType;
    private final Ein filerEin;
    private final String filerXml;
    private final String returnDataXml;
    private final TaxPeriod taxPeriod;
    private final ReportingPeriod period;

    private ClientReturnBody(ClientReturnType returnType,
                             Ein filerEin,
                             String filerXml,
                             String returnDataXml,
                             TaxPeriod taxPeriod,
                             ReportingPeriod period) {
        this.returnType = Objects.requireNonNull(returnType);
        this.filerEin = Objects.requireNonNull(filerEin);
        this.filerXml = Objects.requireNonNull(filerXml);
        this.returnDataXml = Objects.requireNonNull(returnDataXml);
        this.taxPeriod = Objects.requireNonNull(taxPeriod);
        this.period = Objects.requireNonNull(period);
        period.requireMatches(taxPeriod);
    }

    ClientReturnType returnType() {
        return returnType;
    }

    Ein filerEin() {
        return filerEin;
    }

    TaxPeriod taxPeriod() {
        return taxPeriod;
    }

    ReportingPeriod period() {
        return period;
    }

    String returnVersion() {
        return returnType.returnVersion();
    }

    String filerXml() {
        return filerXml;
    }

    String returnDataXml() {
        return returnDataXml;
    }

    /**
     * Parse a ClientReturnBody document. Rejects root {@code Return} (that is how OnlineFiler
     * Scenario 1 is kept from becoming an RA input).
     */
    static ClientReturnBody load(Path xml) {
        Objects.requireNonNull(xml, "xml");
        Document document = parseHardened(xml);
        Element root = document.getDocumentElement();
        if (root == null) {
            throw new ReportingAgentValidationException("xml", "Document has no root element");
        }
        String localName = root.getLocalName();
        if ("Return".equals(localName)) {
            throw new ReportingAgentValidationException("xml",
                    "ClientReturnBody root is required; a complete Return is not a client body");
        }
        if (!"ClientReturnBody".equals(localName) || !NS.equals(root.getNamespaceURI())) {
            throw new ReportingAgentValidationException("xml",
                    "Root must be ClientReturnBody in namespace " + NS + ", found "
                            + localName + " ns=" + root.getNamespaceURI());
        }

        ClientReturnType returnType = ClientReturnType.parse(requiredAttribute(root, "returnTypeCd"));
        String returnVersion = requiredAttribute(root, "returnVersion");
        if (!returnType.returnVersion().equals(returnVersion)) {
            throw new ReportingAgentValidationException("returnVersion",
                    "returnTypeCd " + returnType.returnTypeCd() + " is locked to " + returnType.returnVersion()
                            + ", found " + returnVersion);
        }

        TaxPeriod taxPeriod = new TaxPeriod(
                parseDate("taxPeriodBegin", requiredAttribute(root, "taxPeriodBegin")),
                parseDate("taxPeriodEnd", requiredAttribute(root, "taxPeriodEnd")));
        ReportingPeriod period = reportingPeriod(root, returnType);

        Element filer = child(root, "Filer");
        Element einElement = child(filer, "EIN");
        Ein filerEin = new Ein(einElement.getTextContent().trim());
        Element returnData = child(root, "ReturnData");
        requireMainDocumentFirst(returnData, returnType);

        return new ClientReturnBody(
                returnType,
                filerEin,
                serialize(filer),
                serialize(returnData),
                taxPeriod,
                period);
    }

    /** Reads only the attribute this return type's period kind names; the other kind's attribute is an error. */
    private static ReportingPeriod reportingPeriod(Element root, ClientReturnType returnType) {
        PeriodKind kind = returnType.periodKind();
        for (PeriodKind other : PeriodKind.values()) {
            if (other != kind && root.hasAttribute(other.attribute())) {
                throw new ReportingAgentValidationException(other.attribute(),
                        "returnTypeCd " + returnType.returnTypeCd() + " takes " + kind.attribute()
                                + ", not " + other.attribute());
            }
        }
        return kind.parse(requiredAttribute(root, kind.attribute()));
    }

    private static Document parseHardened(Path xml) {
        try (InputStream in = Files.newInputStream(xml)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            try {
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            } catch (IllegalArgumentException ignored) {
                // Parser does not support JAXP external-access attributes.
            }
            return factory.newDocumentBuilder().parse(in);
        } catch (ReportingAgentValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ReportingAgentValidationException("xml",
                    "Could not parse ClientReturnBody from " + xml + ": " + e.getMessage());
        }
    }

    private static void requireMainDocumentFirst(Element returnData, ClientReturnType returnType) {
        Element first = firstElement(returnData);
        String found = first == null ? "nothing" : first.getLocalName();
        if (first == null || !NS.equals(first.getNamespaceURI())
                || !returnType.mainDocument().equals(found)) {
            throw new ReportingAgentValidationException("returnTypeCd",
                    "returnTypeCd " + returnType.returnTypeCd() + " requires ReturnData to open with "
                            + returnType.mainDocument() + ", found " + found);
        }
    }

    private static Element firstElement(Element parent) {
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element element) {
                return element;
            }
        }
        return null;
    }

    private static Element child(Element parent, String localName) {
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element
                    && NS.equals(element.getNamespaceURI())
                    && localName.equals(element.getLocalName())) {
                return element;
            }
        }
        throw new ReportingAgentValidationException(localName, "Missing required element " + localName);
    }

    private static String requiredAttribute(Element element, String name) {
        String value = element.getAttribute(name);
        if (value == null || value.isBlank()) {
            throw new ReportingAgentValidationException(name, name + " is required");
        }
        return value;
    }

    private static LocalDate parseDate(String field, String raw) {
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new ReportingAgentValidationException(field, field + " is not an ISO date: " + raw);
        }
    }

    private static String serialize(Element element) {
        try {
            DOMImplementationLS ls = (DOMImplementationLS) element.getOwnerDocument()
                    .getImplementation()
                    .getFeature("LS", "3.0");
            LSSerializer serializer = ls.createLSSerializer();
            serializer.getDomConfig().setParameter("xml-declaration", false);
            return serializer.writeToString(element).trim();
        } catch (Exception e) {
            throw new ReportingAgentValidationException("xml",
                    "Could not serialize " + element.getLocalName() + ": " + e.getMessage());
        }
    }
}
