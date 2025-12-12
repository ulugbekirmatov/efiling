package com.irs.mef.config;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads environment variables from .env file into Spring Environment.
 *
 * This processor runs BEFORE @ConfigurationProperties binding, ensuring
 * that .env values are available for property placeholders like ${MEF_ETIN:}.
 *
 * Priority Order (highest to lowest):
 * 1. System environment variables
 * 2. Command-line arguments
 * 3. .env file values (loaded here)
 * 4. application.yml defaults
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE_NAME = "dotenvProperties";
    private static final String DEFAULT_ENV_FILE = ".env";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            // Load .env file from project root (relative to execution directory)
            Dotenv dotenv = Dotenv.configure()
                    .directory(System.getProperty("user.dir"))  // Explicit directory for clarity
                    .filename(DEFAULT_ENV_FILE)
                    .ignoreIfMalformed()  // Don't fail on parsing errors
                    .ignoreIfMissing()    // Don't fail if .env doesn't exist
                    .load();

            // Convert to Map for PropertySource
            Map<String, Object> dotenvMap = new HashMap<>();
            dotenv.entries().forEach(entry -> {
                String key = entry.getKey();
                String value = entry.getValue();

                // Only add if not already set in system environment
                // This ensures system env vars take precedence (important for production)
                if (System.getenv(key) == null) {
                    dotenvMap.put(key, value);
                }
            });

            if (!dotenvMap.isEmpty()) {
                // Add PropertySource with lower priority than system env
                PropertySource<?> propertySource = new MapPropertySource(PROPERTY_SOURCE_NAME, dotenvMap);
                environment.getPropertySources().addLast(propertySource);

                // Log success with masked sensitive values
                System.out.println("[DotenvEnvironmentPostProcessor] Loaded " + dotenvMap.size() + " properties from .env file");
                dotenvMap.forEach((key, value) -> {
                    String stringValue = String.valueOf(value);
                    String displayValue = key.toUpperCase().contains("PASSWORD") ||
                                         key.toUpperCase().contains("KEY")
                                         ? "***"
                                         : stringValue;
                    System.out.println("  " + key + " = " + displayValue);
                });
            } else {
                System.out.println("[DotenvEnvironmentPostProcessor] No .env file found or all values already set in system environment");
            }

        } catch (DotenvException e) {
            // Log but don't fail - application can still work with system env vars
            System.err.println("[DotenvEnvironmentPostProcessor] Warning: Could not load .env file: " + e.getMessage());
        }
    }
}
