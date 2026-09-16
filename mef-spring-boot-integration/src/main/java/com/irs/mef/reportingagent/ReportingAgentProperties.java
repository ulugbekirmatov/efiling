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
    private String softwareId = "";
    private String ein = "";
    private String businessName = "";
    private String nameControl = "";
    private String addressLine1 = "";
    private String city = "";
    private String state = "";
    private String zip = "";
}
