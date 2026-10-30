package com.irs.mef.returnpdf;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A parsed Return plus the ledger of values that must reach paper. Reading a value through a
 * {@link Node} marks it printed; {@link #requireAllPrinted} fails on whatever no form read.
 */
final class ReturnXml {

    static final String NAMESPACE = "http://www.irs.gov/efile";

    /**
     * Header values a paper form shows. The rest of the header is transmission plumbing, and the
     * Reporting Agent PIN group is the signature itself, which never goes on a copy.
     */
    private static final Set<String> PRINTED_HEADER_ELEMENTS = Set.of(
            "Filer", "QuarterEndingDt", "TaxYr", "DiscussWithThirdPartyYesGrp", "DiscussWithThirdPartyNoInd",
            "PaidPreparerInformationGrp");

    /** MeF links documents with these; no IRS form has a box for them. */
    private static final Set<String> LINKAGE_ATTRIBUTES = Set.of(
            "documentId", "documentName", "referenceDocumentId", "referenceDocumentName", "softwareId",
            "softwareVersionNum");

    private final String returnVersion;
    private final Node header;
    private final List<Node> documents;
    private final Set<org.w3c.dom.Node> unprinted;

    private ReturnXml(String returnVersion, Node header, List<Node> documents, Set<org.w3c.dom.Node> unprinted) {
        this.returnVersion = returnVersion;
        this.header = header;
        this.documents = documents;
        this.unprinted = unprinted;
    }

    static ReturnXml parse(String xml) {
        Element root = parseHardened(xml).getDocumentElement();
        if (!"Return".equals(root.getLocalName()) || !NAMESPACE.equals(root.getNamespaceURI())) {
            throw new ReturnPdfException("Root must be Return in " + NAMESPACE + ", found " + root.getLocalName());
        }
        Set<org.w3c.dom.Node> unprinted = new LinkedHashSet<>();
        Element headerElement = onlyChild(root, "ReturnHeader");
        for (Element child : children(headerElement)) {
            if (PRINTED_HEADER_ELEMENTS.contains(child.getLocalName())) {
                collectValues(child, unprinted);
            }
        }
        List<Node> documents = new ArrayList<>();
        for (Element document : children(onlyChild(root, "ReturnData"))) {
            collectValues(document, unprinted);
            documents.add(new Node(document, unprinted));
        }
        if (documents.isEmpty()) {
            throw new ReturnPdfException("ReturnData has no documents");
        }
        return new ReturnXml(root.getAttribute("returnVersion"), new Node(headerElement, unprinted),
                List.copyOf(documents), unprinted);
    }

    String returnVersion() {
        return returnVersion;
    }

    Node header() {
        return header;
    }

    List<Node> documents() {
        return documents;
    }

    void requireAllPrinted() {
        if (!unprinted.isEmpty()) {
            throw new ReturnPdfException("Values sent to IRS that no form prints: "
                    + unprinted.stream().map(ReturnXml::path).collect(Collectors.joining(", ")));
        }
    }

    /** Leaf element text and non-linkage attributes: the values a reader of the paper copy would expect. */
    private static void collectValues(Element element, Set<org.w3c.dom.Node> into) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attribute = (Attr) attributes.item(i);
            if (!XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())
                    && !LINKAGE_ATTRIBUTES.contains(attribute.getLocalName())) {
                into.add(attribute);
            }
        }
        List<Element> children = children(element);
        if (children.isEmpty()) {
            into.add(element);
        }
        children.forEach(child -> collectValues(child, into));
    }

    private static String path(org.w3c.dom.Node node) {
        if (node instanceof Attr attribute) {
            return path(attribute.getOwnerElement()) + "/@" + attribute.getLocalName();
        }
        StringBuilder path = new StringBuilder();
        for (org.w3c.dom.Node at = node; at instanceof Element element
                && !"ReturnData".equals(element.getLocalName())
                && !"ReturnHeader".equals(element.getLocalName()); at = at.getParentNode()) {
            path.insert(0, "/" + element.getLocalName());
        }
        return path.substring(1);
    }

    private static Element onlyChild(Element parent, String localName) {
        List<Element> matches = children(parent).stream()
                .filter(child -> localName.equals(child.getLocalName()))
                .toList();
        if (matches.size() != 1) {
            throw new ReturnPdfException("Expected one " + localName + " under " + parent.getLocalName()
                    + ", found " + matches.size());
        }
        return matches.get(0);
    }

    private static List<Element> children(Element parent) {
        List<Element> elements = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element element && NAMESPACE.equals(element.getNamespaceURI())) {
                elements.add(element);
            }
        }
        return elements;
    }

    private static Document parseHardened(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            return factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new ReturnPdfException("Could not parse Return XML: " + e.getMessage(), e);
        }
    }

    /**
     * One element of the Return. Paths are child local names joined by '/', for example
     * {@code SocialSecurityWageAndTaxGrp/SocialSecurityTaxAmt}; {@code @name} addresses an attribute.
     */
    static final class Node {

        private final Element element;
        private final Set<org.w3c.dom.Node> unprinted;

        private Node(Element element, Set<org.w3c.dom.Node> unprinted) {
            this.element = element;
            this.unprinted = unprinted;
        }

        String name() {
            return element.getLocalName();
        }

        /** The value at {@code path}, marked printed. Empty when the Return omits it. */
        Optional<String> text(String path) {
            return find(path).map(found -> {
                unprinted.remove(found);
                return found.getTextContent().trim();
            });
        }

        /** A CheckboxType value ("X"), marked printed. */
        boolean checked(String path) {
            return text(path).map("X"::equals).orElse(false);
        }

        /** Every child element named {@code localName}, in document order. */
        List<Node> all(String localName) {
            return children(element).stream()
                    .filter(child -> localName.equals(child.getLocalName()))
                    .map(child -> new Node(child, unprinted))
                    .toList();
        }

        Optional<Node> child(String localName) {
            List<Node> matches = all(localName);
            if (matches.size() > 1) {
                throw new ReturnPdfException(name() + " has " + matches.size() + " " + localName + " elements");
            }
            return matches.stream().findFirst();
        }

        /**
         * Declares a value that no box on the paper form shows. The reason is the review record:
         * it names why a reader of the paper copy does not need it.
         */
        void notPrinted(String path, String reason) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("notPrinted(" + path + ") needs a reason");
            }
            find(path).ifPresent(found -> {
                unprinted.remove(found);
                if (found instanceof Element parent) {
                    removeSubtree(parent);
                }
            });
        }

        private void removeSubtree(Element parent) {
            NamedNodeMap attributes = parent.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                unprinted.remove(attributes.item(i));
            }
            children(parent).forEach(child -> {
                unprinted.remove(child);
                removeSubtree(child);
            });
        }

        private Optional<org.w3c.dom.Node> find(String path) {
            String[] steps = path.split("/");
            Element at = element;
            for (int i = 0; i < steps.length; i++) {
                String step = steps[i];
                if (step.startsWith("@")) {
                    if (i != steps.length - 1) {
                        throw new IllegalArgumentException("Attribute must be the last step: " + path);
                    }
                    Attr attribute = at.getAttributeNode(step.substring(1));
                    return Optional.ofNullable(attribute);
                }
                Element current = at;
                List<Element> matches = children(current).stream()
                        .filter(child -> step.equals(child.getLocalName()))
                        .toList();
                if (matches.size() > 1) {
                    throw new ReturnPdfException(path + " matches " + matches.size() + " elements under " + name()
                            + "; read repeating groups with all()");
                }
                if (matches.isEmpty()) {
                    return Optional.empty();
                }
                at = matches.get(0);
            }
            return Optional.of(at);
        }
    }
}
