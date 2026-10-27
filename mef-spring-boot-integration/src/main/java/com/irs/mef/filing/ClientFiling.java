package com.irs.mef.filing;

/**
 * What a caller files. {@link #parse} is the boundary: the client id and the client body XML
 * come in as strings, and everything past this point is a validated value.
 */
public final class ClientFiling {

    /** Hard cap on {@code clientBody}, in characters. {@code FilingProperties} starts at this value. */
    public static final int MAX_CLIENT_BODY_CHARS = 8_000_000;

    private final ClientId clientId;
    private final ClientReturnBody body;
    private final String clientBodyXml;

    private ClientFiling(ClientId clientId, ClientReturnBody body, String clientBodyXml) {
        this.clientId = clientId;
        this.body = body;
        this.clientBodyXml = clientBodyXml;
    }

    public static ClientFiling parse(String clientId, String clientBodyXml) {
        if (clientBodyXml != null && clientBodyXml.length() > MAX_CLIENT_BODY_CHARS) {
            throw new FilingValidationException("clientBody",
                    "clientBody exceeds " + MAX_CLIENT_BODY_CHARS + " characters");
        }
        if (clientBodyXml == null || clientBodyXml.isBlank()) {
            throw new FilingValidationException("clientBody", "clientBody is required");
        }
        try {
            return new ClientFiling(new ClientId(clientId), ClientReturnBody.parse(clientBodyXml), clientBodyXml);
        } catch (ReportingAgentValidationException e) {
            throw new FilingValidationException(e.field(), e.getMessage());
        }
    }

    public ClientId clientId() {
        return clientId;
    }

    public FormSpec form() {
        return body.form();
    }

    public Ein filer() {
        return body.filerEin();
    }

    public TaxPeriod period() {
        return body.taxPeriod();
    }

    public String clientBodyXml() {
        return clientBodyXml;
    }

    ClientReturnBody body() {
        return body;
    }
}
