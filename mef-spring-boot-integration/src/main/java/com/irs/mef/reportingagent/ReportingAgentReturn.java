package com.irs.mef.reportingagent;

import com.irs.mef.newsend.domain.Efin;
import com.irs.mef.newsend.domain.Ein;
import com.irs.mef.newsend.domain.FormType;
import com.irs.mef.newsend.domain.NewSendSubmitCommand;
import com.irs.mef.newsend.domain.TaxPeriod;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * A complete schema-shaped 941 Return whose originator is a Reporting Agent.
 * Constructed only by {@link #compose}. Callers cannot pass OriginatorTypeCd.
 *
 * Hidden: header element order, PIN-group enumerations, FilingSecurityInformation
 * (IPv4 wrapper, hex device ids, 16-char vendor control), binaryAttachmentCnt=0,
 * MultSoftwarePackagesUsedInd=false, omission of IPTimezoneCd, required
 * ReportingAgent94XFilerGrp, absence of OnlineFilerPINGrp.
 *
 * Exposed: xml string, structure probe, NewSend command. The tax body is the
 * client's; the header is Pyramos's.
 */
public final class ReportingAgentReturn {

    public static final String ORIGINATOR_TYPE = "ReportingAgent";
    public static final String RAPIN_ENTERED_BY = "REPORTING AGENT";
    public static final String JURAT = "REPORTING AGENT PIN";

    private static final String NS = ReportingAgentReturnXml.NS;
    private static final ZoneId PROCESSING_ZONE = ZoneId.of("America/New_York");

    private final ReportingAgentOriginator originator;
    private final Client941Body client;
    private final String xml;

    private ReportingAgentReturn(ReportingAgentOriginator originator, Client941Body client, String xml) {
        this.originator = originator;
        this.client = client;
        this.xml = xml;
    }

    public static ReportingAgentReturn compose(
            ReportingAgentOriginator originator,
            Client941Body client,
            Clock clock) {
        Objects.requireNonNull(originator);
        Objects.requireNonNull(client);
        Objects.requireNonNull(clock);
        if (!Client941Body.RETURN_VERSION.equals(client.returnVersion())) {
            throw new ReportingAgentValidationException("returnVersion",
                    "locked to " + Client941Body.RETURN_VERSION);
        }
        OffsetDateTime returnTs = OffsetDateTime.now(clock.withZone(PROCESSING_ZONE));
        String xml = ReportingAgentReturnXml.render(originator, client, returnTs);
        return new ReportingAgentReturn(originator, client, xml);
    }

    public String xml() {
        return xml;
    }

    public Ein clientEin() {
        return client.filerEin();
    }

    public TaxPeriod taxPeriod() {
        return client.taxPeriod();
    }

    /**
     * EIN, form, period, and XML come from this object — they cannot disagree.
     * Form type is always 941. Submission id is still minted by NewSend.
     */
    public NewSendSubmitCommand toSubmitCommand(
            String idempotencyKey,
            String clientId,
            boolean allowDuplicatePeriod) {
        return new NewSendSubmitCommand(
                idempotencyKey,
                clientId,
                client.filerEin(),
                FormType.F941,
                client.taxPeriod(),
                xml,
                allowDuplicatePeriod);
    }

    public Structure structure() {
        try {
            Document document = parseHardened(xml);
            XPath xpath = XPathFactory.newInstance().newXPath();
            xpath.setNamespaceContext(new EfileNamespace());
            return new Structure(
                    attribute(xpath, document, "/efile:Return/@returnVersion"),
                    text(xpath, document, "/efile:Return/efile:ReturnHeader/efile:OriginatorGrp/efile:OriginatorTypeCd"),
                    new Efin(text(xpath, document, "/efile:Return/efile:ReturnHeader/efile:OriginatorGrp/efile:EFIN")),
                    exists(xpath, document, "/efile:Return/efile:ReturnHeader/efile:ReportingAgentPINGrp"),
                    exists(xpath, document, "/efile:Return/efile:ReturnHeader/efile:OnlineFilerPINGrp"),
                    exists(xpath, document, "/efile:Return/efile:ReturnHeader/efile:PractitionerPINGrp"),
                    exists(xpath, document, "/efile:Return/efile:ReturnHeader/efile:ReportingAgent94XFilerGrp"),
                    new Ein(text(xpath, document,
                            "/efile:Return/efile:ReturnHeader/efile:ReportingAgent94XFilerGrp/efile:EIN")),
                    new Ein(text(xpath, document, "/efile:Return/efile:ReturnHeader/efile:Filer/efile:EIN")),
                    text(xpath, document, "/efile:Return/efile:ReturnHeader/efile:SoftwareId"),
                    text(xpath, document,
                            "/efile:Return/efile:ReturnHeader/efile:ReportingAgentPINGrp/efile:RAPINEnteredByCd"),
                    text(xpath, document,
                            "/efile:Return/efile:ReturnHeader/efile:ReportingAgentPINGrp/efile:JuratDisclosureCd"));
        } catch (ReportingAgentValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ReportingAgentValidationException("xml", "Could not probe Return structure: " + e.getMessage());
        }
    }

    private static Document parseHardened(String xml) throws Exception {
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
        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String text(XPath xpath, Document document, String expression) throws XPathExpressionException {
        String value = xpath.evaluate(expression, document);
        if (value == null || value.isBlank()) {
            throw new ReportingAgentValidationException("xml", "Missing " + expression);
        }
        return value.trim();
    }

    private static String attribute(XPath xpath, Document document, String expression) throws XPathExpressionException {
        return text(xpath, document, expression);
    }

    private static boolean exists(XPath xpath, Document document, String expression) throws XPathExpressionException {
        NodeList nodes = (NodeList) xpath.evaluate(expression, document, XPathConstants.NODESET);
        return nodes != null && nodes.getLength() > 0;
    }

    public record Structure(
            String returnVersion,
            String originatorTypeCd,
            Efin originatorEfin,
            boolean hasReportingAgentPinGrp,
            boolean hasOnlineFilerPinGrp,
            boolean hasPractitionerPinGrp,
            boolean hasReportingAgent94XFilerGrp,
            Ein agentEin,
            Ein filerEin,
            String softwareId,
            String rapinEnteredByCd,
            String juratDisclosureCd) {}

    private static final class EfileNamespace implements NamespaceContext {
        @Override
        public String getNamespaceURI(String prefix) {
            return "efile".equals(prefix) ? NS : XMLConstants.NULL_NS_URI;
        }

        @Override
        public String getPrefix(String namespaceURI) {
            return NS.equals(namespaceURI) ? "efile" : null;
        }

        @Override
        public Iterator<String> getPrefixes(String namespaceURI) {
            return List.of("efile").iterator();
        }
    }
}
