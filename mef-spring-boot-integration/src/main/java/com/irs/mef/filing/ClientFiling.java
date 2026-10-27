package com.irs.mef.filing;

public final class ClientFiling {

    public static final int MAX_CLIENT_BODY_CHARS = 8_000_000;

    private final ClientId clientId;
    private final ClientReturnBody body;
    private final String clientBodyXml;
    private final boolean allowDuplicatePeriod;

    private ClientFiling(ClientId clientId, ClientReturnBody body, String clientBodyXml,
                         boolean allowDuplicatePeriod) {
        this.clientId = clientId;
        this.body = body;
        this.clientBodyXml = clientBodyXml;
        this.allowDuplicatePeriod = allowDuplicatePeriod;
    }

    public static ClientFiling parse(String clientId, String clientBodyXml) {
        return parse(clientId, clientBodyXml, false);
    }

    public static ClientFiling parse(String clientId, String clientBodyXml, boolean allowDuplicatePeriod) {
        if (clientBodyXml != null && clientBodyXml.length() > MAX_CLIENT_BODY_CHARS) {
            throw new FilingValidationException("clientBody",
                    "clientBody exceeds " + MAX_CLIENT_BODY_CHARS + " characters");
        }
        if (clientBodyXml == null || clientBodyXml.isBlank()) {
            throw new FilingValidationException("clientBody", "clientBody is required");
        }
        try {
            return new ClientFiling(new ClientId(clientId), ClientReturnBody.parse(clientBodyXml),
                    clientBodyXml, allowDuplicatePeriod);
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

    public boolean allowDuplicatePeriod() {
        return allowDuplicatePeriod;
    }

    ClientReturnBody body() {
        return body;
    }
}
