package com.irs.mef.filing;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The reject codes preflight can reproduce. There is no force flag: a missing rule blocks the next live send. */
public final class LocalRules {

    private static final List<LocalRule> ALL = List.of(
            new OnlineFilerEfin(),
            new RoutableFilingIp(),
            new ThreeEfinPlaces(),
            new ManifestEfileNamespace());

    private static final Pattern MANIFEST_EFIN = Pattern.compile("<EFIN>([^<]+)</EFIN>");
    private static final Pattern ORIGINATOR_EFIN = Pattern.compile(
            "<OriginatorGrp>\\s*<EFIN>([^<]+)</EFIN>", Pattern.DOTALL);
    private static final Pattern ORIGINATOR_TYPE = Pattern.compile("<OriginatorTypeCd>([^<]+)</OriginatorTypeCd>");
    private static final Pattern SUBMISSION_ID = Pattern.compile("<SubmissionId>([^<]+)</SubmissionId>");
    private static final Pattern IPV4 = Pattern.compile("<IPv4AddressTxt>([^<]+)</IPv4AddressTxt>");

    private LocalRules() {}

    public static List<RuleResult> check(Preflight preflight) {
        return ALL.stream().map(rule -> rule.check(preflight)).toList();
    }

    public static boolean covers(String irsCode) {
        return irsCode != null && ALL.stream().anyMatch(rule -> rule.irsCode().equals(irsCode));
    }

    static List<LocalRule> all() {
        return ALL;
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String group(Pattern pattern, String xml) {
        Matcher matcher = pattern.matcher(xml);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static RuleResult pass(String code) {
        return new RuleResult(code, true, "");
    }

    private static RuleResult fail(String code, String detail) {
        return new RuleResult(code, false, detail);
    }

    /** R0000-118-01: a manifest EFIN starting 10/21/32/44/53 cannot file as ReportingAgent. */
    private static final class OnlineFilerEfin implements LocalRule {
        private static final String CODE = "R0000-118-01";

        @Override
        public String irsCode() {
            return CODE;
        }

        @Override
        public RuleResult check(Preflight preflight) {
            String efin = group(MANIFEST_EFIN, text(preflight.manifestXml()));
            if (efin == null || efin.length() < 2) {
                return pass(CODE);
            }
            String prefix = efin.substring(0, 2);
            if (!Originator.ONLINE_FILER_EFIN_PREFIXES.contains(prefix)) {
                return pass(CODE);
            }
            String type = group(ORIGINATOR_TYPE, text(preflight.returnXml()));
            if ("ReportingAgent".equals(type) || type == null) {
                return fail(CODE, "manifest EFIN " + efin + " starts with " + prefix
                        + " and OriginatorTypeCd is " + (type == null ? "missing" : type));
            }
            return pass(CODE);
        }

        @Override
        public String rejectedSample() {
            return "rejects/R0000-118-01";
        }
    }

    /** R0000-244: loopback and the unspecified address are not a filing IP. */
    private static final class RoutableFilingIp implements LocalRule {
        private static final String CODE = "R0000-244";

        @Override
        public String irsCode() {
            return CODE;
        }

        @Override
        public RuleResult check(Preflight preflight) {
            String address = group(IPV4, text(preflight.returnXml()));
            if (address == null) {
                return fail(CODE, "IPv4AddressTxt is missing");
            }
            if (address.startsWith("127.") || address.equals("0.0.0.0")) {
                return fail(CODE, "filing IP " + address + " is not routable");
            }
            return pass(CODE);
        }

        @Override
        public String rejectedSample() {
            return "rejects/R0000-244";
        }
    }

    /** R0000-054-01: manifest EFIN, OriginatorGrp EFIN, and the submission id prefix are one EFIN. */
    private static final class ThreeEfinPlaces implements LocalRule {
        private static final String CODE = "R0000-054-01";

        @Override
        public String irsCode() {
            return CODE;
        }

        @Override
        public RuleResult check(Preflight preflight) {
            String manifest = text(preflight.manifestXml());
            String manifestEfin = group(MANIFEST_EFIN, manifest);
            String originatorEfin = group(ORIGINATOR_EFIN, text(preflight.returnXml()));
            String submissionId = group(SUBMISSION_ID, manifest);
            String idPrefix = submissionId != null && submissionId.length() >= 6
                    ? submissionId.substring(0, 6) : null;
            if (manifestEfin == null || originatorEfin == null || idPrefix == null) {
                return fail(CODE, "EFIN places are incomplete");
            }
            if (manifestEfin.equals(originatorEfin) && manifestEfin.equals(idPrefix)) {
                return pass(CODE);
            }
            return fail(CODE, "manifest EFIN " + manifestEfin
                    + ", OriginatorGrp EFIN " + originatorEfin
                    + ", id prefix " + idPrefix);
        }

        @Override
        public String rejectedSample() {
            return "rejects/R0000-054-01";
        }
    }

    /** X0000-008: the manifest root must bind the efile prefix. The manifest XSD does not require it. */
    private static final class ManifestEfileNamespace implements LocalRule {
        private static final String CODE = "X0000-008";

        @Override
        public String irsCode() {
            return CODE;
        }

        @Override
        public RuleResult check(Preflight preflight) {
            String manifest = text(preflight.manifestXml());
            int start = manifest.indexOf("<IRSSubmissionManifest");
            int end = start < 0 ? -1 : manifest.indexOf('>', start);
            if (start < 0 || end < 0) {
                return fail(CODE, "manifest root is missing");
            }
            String tag = manifest.substring(start, end);
            if (tag.contains("xmlns:efile=\"" + Efile.NAMESPACE + "\"")) {
                return pass(CODE);
            }
            return fail(CODE, "manifest root lacks xmlns:efile");
        }

        @Override
        public String rejectedSample() {
            return "rejects/X0000-008";
        }
    }
}
