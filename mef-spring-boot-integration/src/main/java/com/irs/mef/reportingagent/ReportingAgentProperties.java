package com.irs.mef.reportingagent;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "mef.reporting-agent")
@Data
public class ReportingAgentProperties {
    /** MEF_RA_PIN. Empty at boot is allowed; compose-from-env fails closed. */
    private String pin = "";
    /** MEF_SOFTWARE_ID. The 94x quarterly package (941, 941X). */
    private String softwareId = "";
    /** MEF_SOFTWARE_ID_ANNUAL. The 94x annual package (940); optional until annual testing opens. */
    private String annualSoftwareId = "";
    /** MEF_RA_FILING_IP. Public IPv4 of the transmitting host; R0000-244 rejects loopback. */
    private String filingIp = "";
    private String ein = "";
    private String businessName = "";
    private String nameControl = "";
    private String addressLine1 = "";
    private String city = "";
    private String state = "";
    private String zip = "";
}
