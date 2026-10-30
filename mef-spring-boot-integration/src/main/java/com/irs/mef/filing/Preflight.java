package com.irs.mef.filing;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class Preflight {

    private final ClientFiling filing;
    private final Filing prepared;
    private final String manifestXml;
    private final byte[] archive;
    private final List<RuleResult> rules;
    private final String manifestSchemaReport;
    private final Cleared cleared;

    private Preflight(ClientFiling filing, Filing prepared, String manifestXml,
                      byte[] archive, List<RuleResult> rules, String manifestSchemaReport) {
        this.filing = filing;
        this.prepared = prepared;
        this.manifestXml = manifestXml;
        this.archive = archive;
        this.rules = rules;
        this.manifestSchemaReport = manifestSchemaReport;
        this.cleared = new Cleared();
    }

    static Preflight of(ClientFiling filing, Filing prepared, String manifestXml) {
        return new Preflight(filing, prepared, manifestXml, new byte[0], List.of(), "");
    }

    Preflight finish(byte[] builtArchive, List<RuleResult> ruleResults, String schemaReport) {
        return new Preflight(filing, prepared, manifestXml, builtArchive, List.copyOf(ruleResults), schemaReport);
    }

    String manifestText() {
        return manifestXml;
    }

    public ClientFiling filing() {
        return filing;
    }

    public SubmissionId id() {
        return prepared.submissionId();
    }

    public byte[] manifestXml() {
        return manifestXml.getBytes(StandardCharsets.UTF_8);
    }

    public byte[] returnXml() {
        return prepared.command().returnXml().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] archive() {
        return archive;
    }

    public List<RuleResult> rules() {
        return rules;
    }

    public List<RuleResult> failures() {
        return rules.stream().filter(rule -> !rule.passed()).toList();
    }

    public String manifestSchemaReport() {
        return manifestSchemaReport;
    }

    Cleared cleared() {
        return cleared;
    }

    Filing prepared() {
        return prepared;
    }

    public final class Cleared {
        private Cleared() {}

        Filing prepared() {
            return prepared;
        }

        String manifestXml() {
            return manifestXml;
        }
    }
}
