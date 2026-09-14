package com.irs.mef.inspector.capture;

import com.irs.mef.inspector.Sha256Hex;
import com.irs.mef.inspector.ring.CapturedXml;
import com.irs.mef.inspector.ring.MimeRequest;
import com.irs.mef.inspector.ring.OmittedAttachment;
import com.irs.mef.inspector.ring.SoapCapture;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SOAP part + attachment descriptors. Never {@code SOAPMessage.writeTo()} the whole multipart
 * (that is the JUL dump). ZIP bytes are hashed and dropped.
 */
@Slf4j
public final class MimeMessageReader {

    static final long DEFAULT_MAX_XML_BYTES = 1_048_576L;

    private MimeMessageReader() {
    }

    /**
     * @param soapMessage javax or jakarta SOAPMessage
     */
    public static MimeRequest read(Object soapMessage) {
        return read(soapMessage, DEFAULT_MAX_XML_BYTES);
    }

    public static MimeRequest read(Object soapMessage, long maxXmlBytes) {
        if (soapMessage == null) {
            return missing("SOAPMessage was null");
        }
        try {
            String contentType = header(soapMessage, "Content-Type");
            SoapCapture soapPart = soapFromSaaj(soapMessage, maxXmlBytes);
            List<OmittedAttachment> attachments = attachmentsFromSaaj(soapMessage);
            return new MimeRequest(contentType, soapPart, attachments);
        } catch (Exception e) {
            log.warn("inspector: MIME read from SOAPMessage failed: {}", e.toString());
            return missing("MIME read failed: " + e.getClass().getSimpleName());
        }
    }

    /**
     * Checked-in {@code .mime} fixture path: RFC822 headers (must include Content-Type) then body.
     */
    public static MimeRequest readMimeFile(byte[] rfc822) {
        return readMimeFile(rfc822, DEFAULT_MAX_XML_BYTES);
    }

    public static MimeRequest readMimeFile(byte[] rfc822, long maxXmlBytes) {
        ParsedMime parsed = splitRfc822(rfc822);
        return readRawMime(parsed.body(), parsed.contentType(), maxXmlBytes);
    }

    public static MimeRequest readRawMime(byte[] body, String contentType, long maxXmlBytes) {
        try {
            String type = contentType == null ? "" : contentType;
            String boundary = boundaryOf(type);
            if (boundary == null || boundary.isBlank()) {
                return new MimeRequest(type, soapFromXmlBytes(body, maxXmlBytes), List.of());
            }
            List<RawPart> parts = splitMultipart(body, boundary);
            SoapCapture soap = new SoapCapture.Missing("no text/xml part in MIME");
            List<OmittedAttachment> attachments = new ArrayList<>();
            boolean soapTaken = false;
            for (RawPart part : parts) {
                String partType = part.header("content-type");
                if (!soapTaken && isSoapPart(partType)) {
                    soap = soapFromXmlBytes(part.body, maxXmlBytes);
                    soapTaken = true;
                } else {
                    attachments.add(omit(part));
                }
            }
            return new MimeRequest(type, soap, attachments);
        } catch (Exception e) {
            log.warn("inspector: raw MIME split failed: {}", e.toString());
            return missing("MIME split failed: " + e.getClass().getSimpleName());
        }
    }

    private static boolean isSoapPart(String contentType) {
        if (contentType == null) {
            return false;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        return lower.startsWith("text/xml")
                || lower.startsWith("application/soap+xml")
                || lower.startsWith("application/xml");
    }

    private static SoapCapture soapFromXmlBytes(byte[] xmlBytes, long maxXmlBytes) {
        if (xmlBytes == null || xmlBytes.length == 0) {
            return new SoapCapture.Missing("empty SOAP part");
        }
        if (xmlBytes.length > maxXmlBytes) {
            log.warn("inspector: SOAP part {} bytes exceeds max-xml-bytes {}; omitting SOAP",
                    xmlBytes.length, maxXmlBytes);
            return new SoapCapture.Missing("soap part exceeds max-xml-bytes");
        }
        String xml = new String(xmlBytes, StandardCharsets.UTF_8);
        String redacted = SoapRedactor.redact(xml);
        if (redacted == null || redacted.isBlank()) {
            return new SoapCapture.Missing("soap parse failed");
        }
        if (SoapRedactor.containsCredential(redacted)) {
            return new SoapCapture.Missing("redaction failed");
        }
        try {
            return new SoapCapture.Present(new CapturedXml(redacted));
        } catch (IllegalArgumentException e) {
            return new SoapCapture.Missing("redacted SOAP was empty");
        }
    }

    private static SoapCapture soapFromSaaj(Object soapMessage, long maxXmlBytes) {
        try {
            Object soapPart = invoke(soapMessage, "getSOAPPart");
            String xml = nodeToString(soapPart);
            if (xml == null || xml.isBlank()) {
                Object envelope = invoke(soapPart, "getEnvelope");
                xml = nodeToString(envelope);
            }
            if (xml == null) {
                return new SoapCapture.Missing("SOAP part could not be serialized");
            }
            return soapFromXmlBytes(xml.getBytes(StandardCharsets.UTF_8), maxXmlBytes);
        } catch (Exception e) {
            log.warn("inspector: SOAP part extract failed: {}", e.toString());
            return new SoapCapture.Missing("SOAP part extract failed");
        }
    }

    private static List<OmittedAttachment> attachmentsFromSaaj(Object soapMessage) {
        List<OmittedAttachment> attachments = new ArrayList<>();
        try {
            Object iteratorObj = invoke(soapMessage, "getAttachments");
            if (!(iteratorObj instanceof Iterator<?> iterator)) {
                return attachments;
            }
            while (iterator.hasNext()) {
                Object part = iterator.next();
                try {
                    attachments.add(omitSaajPart(part));
                } catch (Exception e) {
                    log.warn("inspector: attachment inventory failed: {}", e.toString());
                }
            }
        } catch (Exception e) {
            log.warn("inspector: getAttachments failed: {}", e.toString());
        }
        return attachments;
    }

    private static OmittedAttachment omitSaajPart(Object part) throws Exception {
        String contentId = String.valueOf(invoke(part, "getContentId"));
        String contentType = String.valueOf(invoke(part, "getContentType"));
        Object dataHandler = invoke(part, "getDataHandler");
        Object streamObj = invoke(dataHandler, "getInputStream");
        if (!(streamObj instanceof InputStream in)) {
            throw new IllegalStateException("attachment has no InputStream");
        }
        try (InputStream stream = in) {
            Sha256Hex.Digest digest = Sha256Hex.of(stream);
            return new OmittedAttachment(contentId, contentType, digest.byteLength(), digest.hex());
        }
    }

    private static OmittedAttachment omit(RawPart part) {
        Sha256Hex.Digest digest = Sha256Hex.ofBytes(part.body);
        return new OmittedAttachment(part.header("content-id"), part.header("content-type"),
                digest.byteLength(), digest.hex());
    }

    private static String header(Object soapMessage, String name) {
        try {
            Object headers = invoke(soapMessage, "getMimeHeaders");
            Object values = invoke(headers, "getHeader", new Class<?>[]{String.class}, name);
            if (values instanceof String[] array && array.length > 0) {
                return array[0];
            }
        } catch (Exception ignored) {
            // optional
        }
        return "";
    }

    private static String nodeToString(Object node) {
        if (node == null) {
            return null;
        }
        if (node instanceof org.w3c.dom.Node dom) {
            try {
                javax.xml.transform.Transformer transformer =
                        javax.xml.transform.TransformerFactory.newInstance().newTransformer();
                transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "no");
                java.io.StringWriter writer = new java.io.StringWriter();
                transformer.transform(new javax.xml.transform.dom.DOMSource(dom),
                        new javax.xml.transform.stream.StreamResult(writer));
                return writer.toString();
            } catch (Exception e) {
                return null;
            }
        }
        return String.valueOf(node);
    }

    private static MimeRequest missing(String reason) {
        return new MimeRequest("", new SoapCapture.Missing(reason), List.of());
    }

    private static Object invoke(Object target, String name) throws Exception {
        return invoke(target, name, new Class<?>[0]);
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                var method = type.getDeclaredMethod(name, types);
                method.setAccessible(true);
                return method.invoke(target, args);
            } catch (NoSuchMethodException e) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "#" + name);
    }

    static ParsedMime splitRfc822(byte[] rfc822) {
        int split = indexOf(rfc822, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII), 0);
        int headerEndLen = 4;
        if (split < 0) {
            split = indexOf(rfc822, "\n\n".getBytes(StandardCharsets.US_ASCII), 0);
            headerEndLen = 2;
        }
        if (split < 0) {
            return new ParsedMime("", rfc822);
        }
        String headerBlock = new String(rfc822, 0, split, StandardCharsets.US_ASCII);
        String contentType = "";
        for (String line : headerBlock.split("\r?\n")) {
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            if (line.substring(0, colon).equalsIgnoreCase("Content-Type")) {
                contentType = line.substring(colon + 1).trim();
            }
        }
        byte[] body = Arrays.copyOfRange(rfc822, split + headerEndLen, rfc822.length);
        return new ParsedMime(contentType, body);
    }

    static String boundaryOf(String contentType) {
        if (contentType == null) {
            return null;
        }
        for (String param : contentType.split(";")) {
            String trimmed = param.trim();
            int eq = trimmed.indexOf('=');
            if (eq < 0) {
                continue;
            }
            if (trimmed.substring(0, eq).equalsIgnoreCase("boundary")) {
                String value = trimmed.substring(eq + 1).trim();
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                    return value.substring(1, value.length() - 1);
                }
                return value;
            }
        }
        return null;
    }

    static List<RawPart> splitMultipart(byte[] body, String boundary) {
        byte[] needle = ("--" + boundary).getBytes(StandardCharsets.US_ASCII);
        List<Integer> positions = new ArrayList<>();
        int idx = indexOf(body, needle, 0);
        while (idx >= 0) {
            positions.add(idx);
            idx = indexOf(body, needle, idx + needle.length);
        }
        List<RawPart> parts = new ArrayList<>();
        for (int i = 0; i < positions.size() - 1; i++) {
            int start = positions.get(i) + needle.length;
            if (start + 1 < body.length && body[start] == '-' && body[start + 1] == '-') {
                break;
            }
            start = skipEol(body, start);
            int end = positions.get(i + 1);
            end = stripTrailingEol(body, end);
            if (end < start) {
                end = start;
            }
            parts.add(parsePart(Arrays.copyOfRange(body, start, end)));
        }
        return parts;
    }

    static RawPart parsePart(byte[] partBytes) {
        int split = indexOf(partBytes, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII), 0);
        int sep = 4;
        if (split < 0) {
            split = indexOf(partBytes, "\n\n".getBytes(StandardCharsets.US_ASCII), 0);
            sep = 2;
        }
        Map<String, String> headers = new LinkedHashMap<>();
        byte[] body;
        if (split < 0) {
            body = partBytes;
        } else {
            String headerBlock = new String(partBytes, 0, split, StandardCharsets.US_ASCII);
            for (String line : headerBlock.split("\r?\n")) {
                int colon = line.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                        line.substring(colon + 1).trim());
            }
            body = Arrays.copyOfRange(partBytes, split + sep, partBytes.length);
        }
        return new RawPart(headers, body);
    }

    private static int skipEol(byte[] bytes, int start) {
        int i = start;
        if (i < bytes.length && bytes[i] == '\r') {
            i++;
        }
        if (i < bytes.length && bytes[i] == '\n') {
            i++;
        }
        return i;
    }

    private static int stripTrailingEol(byte[] bytes, int end) {
        if (end >= 2 && bytes[end - 2] == '\r' && bytes[end - 1] == '\n') {
            return end - 2;
        }
        if (end >= 1 && bytes[end - 1] == '\n') {
            return end - 1;
        }
        return end;
    }

    static int indexOf(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int i = from; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    record ParsedMime(String contentType, byte[] body) {
    }

    static final class RawPart {
        final Map<String, String> headers;
        final byte[] body;

        RawPart(Map<String, String> headers, byte[] body) {
            this.headers = headers;
            this.body = body;
        }

        String header(String name) {
            return headers.getOrDefault(name.toLowerCase(Locale.ROOT), "");
        }
    }
}
