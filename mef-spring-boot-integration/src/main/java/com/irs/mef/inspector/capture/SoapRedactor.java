package com.irs.mef.inspector.capture;

import com.irs.mef.inspector.ring.CapturedXml;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Drop WS-Security credentials before disk or HTML. Preserve Timestamp, MeFHeader, body.
 */
@Slf4j
public final class SoapRedactor {

    private static final Set<String> DROP_LOCAL_NAMES =
            Set.of("Assertion", "UsernameToken", "BinarySecurityToken");

    private SoapRedactor() {
    }

    public static String redact(String soapXml) {
        if (soapXml == null || soapXml.isBlank()) {
            return soapXml;
        }
        try {
            Document document = PrettyXml.parse(soapXml);
            List<Element> drop = new ArrayList<>();
            NodeList elements = document.getElementsByTagName("*");
            for (int i = 0; i < elements.getLength(); i++) {
                Element element = (Element) elements.item(i);
                if (DROP_LOCAL_NAMES.contains(localName(element))) {
                    drop.add(element);
                }
            }
            for (Element element : drop) {
                Node parent = element.getParentNode();
                if (parent == null) {
                    continue;
                }
                Comment comment = document.createComment(" credential omitted ");
                parent.insertBefore(comment, element);
                parent.removeChild(element);
            }
            return serialize(document);
        } catch (Exception e) {
            log.warn("inspector: SOAP redaction parse failed; SOAP part will be omitted: {}", e.toString());
            return null;
        }
    }

    public static void assertNoCredentials(CapturedXml xml) {
        if (xml == null) {
            return;
        }
        String original = xml.original();
        if (containsCredential(original)) {
            throw new IllegalArgumentException("SOAP still contains SAML or WS-Security credentials");
        }
    }

    public static boolean containsCredential(String xml) {
        if (xml == null) {
            return false;
        }
        return xml.contains("saml:Assertion")
                || xml.contains("samlAssertion")
                || xml.contains("UsernameToken")
                || xml.contains("BinarySecurityToken");
    }

    private static String localName(Element element) {
        String local = element.getLocalName();
        if (local != null && !local.isBlank()) {
            return local;
        }
        String tag = element.getTagName();
        int colon = tag.indexOf(':');
        return colon >= 0 ? tag.substring(colon + 1) : tag;
    }

    private static String serialize(Document document) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        } catch (IllegalArgumentException ignored) {
            // optional
        }
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }
}
