package com.irs.mef.reportingagent;

import com.irs.mef.config.MefSdkConfig;
import com.irs.mef.newsend.domain.Efin;
import com.irs.mef.newsend.domain.Ein;
import org.springframework.stereotype.Component;

import static com.irs.mef.reportingagent.ReportingAgentChecks.requireConfigured;

@Component
public class ReportingAgentOriginatorFactory {
    private static final String ONEWELL_EFIN = "238689";
    private static final String STAND_IN_EFIN = "000000";
    private static final String ORCHID_EIN = "003000004";

    private final ReportingAgentProperties properties;
    private final MefSdkConfig sdk;

    public ReportingAgentOriginatorFactory(ReportingAgentProperties properties, MefSdkConfig sdk) {
        this.properties = properties;
        this.sdk = sdk;
    }

    /**
     * Live originator. Reads MEF_EFIN from sdk.authentication.efin and RA fields from properties.
     * Throws ReportingAgentValidationException naming the missing env var. Never falls back
     * to ReportingAgentOriginatorStandIn.
     */
    public ReportingAgentOriginator fromEnvironment() {
        String efinRaw = sdk.getAuthentication() != null ? sdk.getAuthentication().getEfin() : null;
        String efinValue = requireConfigured("MEF_EFIN", efinRaw);
        if (ONEWELL_EFIN.equals(efinValue)) {
            throw new ReportingAgentValidationException("efin",
                    "MEF_EFIN 238689 is the OneWell originator; refuse");
        }
        if (STAND_IN_EFIN.equals(efinValue)) {
            throw new ReportingAgentValidationException("efin",
                    "MEF_EFIN 000000 is the schema stand-in; refuse");
        }
        String einRaw = requireConfigured("MEF_RA_EIN", properties.getEin());
        if (ORCHID_EIN.equals(einRaw)) {
            throw new ReportingAgentValidationException("ein",
                    "MEF_RA_EIN 003000004 is the Orchid client EIN; refuse");
        }
        return new ReportingAgentOriginator(
                new Efin(efinValue),
                new ReportingAgentPin(requireConfigured("MEF_RA_PIN", properties.getPin())),
                new SoftwareId(requireConfigured("MEF_SOFTWARE_ID", properties.getSoftwareId())),
                new ReportingAgentIdentity(
                        new Ein(einRaw),
                        requireConfigured("MEF_RA_NAME", properties.getBusinessName()),
                        new NameControl(requireConfigured("MEF_RA_NAME_CONTROL", properties.getNameControl())),
                        new UsAddress(
                                requireConfigured("MEF_RA_ADDRESS_LINE1", properties.getAddressLine1()),
                                requireConfigured("MEF_RA_CITY", properties.getCity()),
                                requireConfigured("MEF_RA_STATE", properties.getState()),
                                requireConfigured("MEF_RA_ZIP", properties.getZip()))));
    }
}
