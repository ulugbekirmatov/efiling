package com.irs.mef;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot application class for IRS MeF Client SDK integration.
 *
 * This application provides REST API endpoints to interact with IRS Modernized e-File (MeF)
 * Application-to-Application (A2A) services using the MeF Client SDK v16.
 *
 * Key Features:
 * - Authentication (Login/Logout) with IRS MeF servers
 * - Submit tax returns (SendSubmissions)
 * - Query submission status (GetSubmissionStatus)
 * - Retrieve acknowledgments (GetAck, GetNewAcks)
 *
 * @author MeF Integration Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableScheduling
public class MefSpringBootApplication {

    public static void main(String[] args) {
        // Set system properties for MeF SDK before Spring Boot initialization
        configureMefSdkSystemProperties();

        SpringApplication.run(MefSpringBootApplication.class, args);
    }

    /**
     * Configure required system properties for MeF Client SDK.
     * These properties must be set before SDK initialization.
     */
    private static void configureMefSdkSystemProperties() {
        // A2A_TOOLKIT_HOME - Set if not already configured
        String toolkitHome = System.getProperty("A2A_TOOLKIT_HOME");
        if (toolkitHome == null || toolkitHome.isEmpty()) {
            // Default to classpath resource location
            String defaultHome = System.getProperty("user.dir") + "/src/main/resources/mef_config";
            System.setProperty("A2A_TOOLKIT_HOME", defaultHome);
            System.out.println("A2A_TOOLKIT_HOME set to: " + defaultHome);
        }

        // Java endorsed directories for Metro web services libraries
        String endorsedDirs = System.getProperty("java.endorsed.dirs");
        if (endorsedDirs == null || endorsedDirs.isEmpty()) {
            String libPath = System.getProperty("user.dir") + "/lib";
            System.setProperty("java.endorsed.dirs", libPath);
            System.out.println("java.endorsed.dirs set to: " + libPath);
        }
    }
}
