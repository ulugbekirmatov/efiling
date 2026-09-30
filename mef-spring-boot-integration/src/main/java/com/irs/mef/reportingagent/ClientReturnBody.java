package com.irs.mef.reportingagent;

import com.irs.mef.newsend.domain.Ein;
import com.irs.mef.newsend.domain.TaxPeriod;
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
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Set;

/**
 * Employer return body for one client. Owns the return type, filer EIN, tax period, quarter
 * ending, and the opaque ReturnData XML (the main form plus any schedules).
 * Does not contain OriginatorGrp, any PIN group, SoftwareId, or ReportingAgent94XFilerGrp.
 */
public final class ClientReturnBody {

    public static final String RETURN_VERSION = "2026Q1v4.0";
    static final String NS = "http://www.irs.gov/efile";

    private static final Set<Integer> QUARTER_MONTHS = Set.of(3, 6, 9, 12);

    private final ClientReturnType returnType;
    private final Ein filerEin;
    private final String filerXml;
    private final String returnDataXml;
    private final TaxPeriod taxPeriod;
    private final YearMonth quarterEnding;
    private final String returnVersion;

    private ClientReturnBody(ClientReturnType returnType,
                             Ein filerEin,
                             String filerXml,
                             String returnDataXml,
                             TaxPeriod taxPeriod,
                             YearMonth quarterEnding,
                             String returnVersion) {
        this.returnType = Objects.requireNonNull(returnType);
        this.filerEin = Objects.requireNonNull(filerEin);
        this.filerXml = Objects.requireNonNull(filerXml);
        this.returnDataXml = Objects.requireNonNull(returnDataXml);
        this.taxPeriod = Objects.requireNonNull(taxPeriod);
        this.quarterEnding = Objects.requireNonNull(quarterEnding);
        this.returnVersion = Objects.requireNonNull(returnVersion);
        if (!QUARTER_MONTHS.contains(quarterEnding.getMonthValue())) {
            throw new ReportingAgentValidationException("quarterEndingDt",
                    "quarterEndingDt month must be 03, 06, 09, or 12");
        }
        if (!YearMonth.from(taxPeriod.end()).equals(quarterEnding)) {
            throw new ReportingAgentValidationException("quarterEndingDt",
                    "quarterEndingDt " + quarterEnding + " must equal tax period end year-month "
                            + YearMonth.from(taxPeriod.end()));
        }
    }

    public ClientReturnType returnType() {
        return returnType;
    }

    public Ein filerEin() {
        return filerEin;
    }

    public TaxPeriod taxPeriod() {
        return taxPeriod;
    }

    public YearMonth quarterEnding() {
        return quarterEnding;
    }

    public String returnVersion() {
        return returnVersion;
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
    public static ClientReturnBody load(Path xml) {
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

        String returnVersion = requiredAttribute(root, "returnVersion");
        if (!RETURN_VERSION.equals(returnVersion)) {
            throw new ReportingAgentValidationException("returnVersion",
                    "locked to " + RETURN_VERSION + ", found " + returnVersion);
        }
        ClientReturnType returnType = ClientReturnType.parse(requiredAttribute(root, "returnTypeCd"));

        TaxPeriod taxPeriod = new TaxPeriod(
                parseDate("taxPeriodBegin", requiredAttribute(root, "taxPeriodBegin")),
                parseDate("taxPeriodEnd", requiredAttribute(root, "taxPeriodEnd")));
        YearMonth quarterEnding = parseYearMonth("quarterEndingDt", requiredAttribute(root, "quarterEndingDt"));

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
                quarterEnding,
                returnVersion);
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

    private static YearMonth parseYearMonth(String field, String raw) {
        try {
            return YearMonth.parse(raw);
        } catch (DateTimeParseException e) {
            throw new ReportingAgentValidationException(field, field + " is not yyyy-MM: " + raw);
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
