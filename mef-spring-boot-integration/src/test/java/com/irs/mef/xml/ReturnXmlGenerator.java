package com.irs.mef.xml;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.UUID;

/**
 * Utility class for generating dynamic values for Return XML files.
 * Provides methods for timestamps, IDs, formatting, and other XML generation needs.
 */
@Slf4j
public class ReturnXmlGenerator {

    private static final DateTimeFormatter ISO_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DecimalFormat AMOUNT_FORMATTER = new DecimalFormat("0.00");
    private static final Random RANDOM = new Random();

    /**
     * Generate an ISO 8601 formatted timestamp with timezone.
     * Example: "2025-12-03T10:30:00-05:00"
     *
     * @return Formatted timestamp string
     */
    public static String generateReturnTimestamp() {
        return LocalDateTime.now()
                .atZone(ZoneId.systemDefault())
                .format(ISO_TIMESTAMP_FORMATTER);
    }

    /**
     * Generate a return timestamp for a specific date and time.
     *
     * @param dateTime The date and time
     * @return Formatted timestamp string
     */
    public static String generateReturnTimestamp(LocalDateTime dateTime) {
        return dateTime
                .atZone(ZoneId.systemDefault())
                .format(ISO_TIMESTAMP_FORMATTER);
    }

    /**
     * Generate a date in YYYY-MM-DD format.
     *
     * @return Formatted date string
     */
    public static String generateDate() {
        return LocalDateTime.now().format(DATE_FORMATTER);
    }

    /**
     * Generate a date in YYYY-MM-DD format for a specific LocalDateTime.
     *
     * @param dateTime The date and time
     * @return Formatted date string
     */
    public static String generateDate(LocalDateTime dateTime) {
        return dateTime.format(DATE_FORMATTER);
    }

    /**
     * Generate a 16-character vendor control number.
     * Format: TEST followed by 12 random digits.
     *
     * @return 16-character vendor control number
     */
    public static String generateVendorControlNum() {
        StringBuilder vcn = new StringBuilder("TEST");
        for (int i = 0; i < 12; i++) {
            vcn.append(RANDOM.nextInt(10));
        }
        return vcn.toString();
    }

    /**
     * Generate a device ID.
     * Format: DEVICE followed by 3 random digits.
     *
     * @return Device ID string
     */
    public static String generateDeviceId() {
        return String.format("DEVICE%03d", RANDOM.nextInt(1000));
    }

    /**
     * Generate a unique submission ID.
     * Format: UUID without hyphens, uppercase.
     *
     * @return Submission ID string
     */
    public static String generateSubmissionId() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    /**
     * Get the local IP address of the machine.
     *
     * @return IP address string, defaults to "127.0.0.1" if unable to determine
     */
    public static String getLocalIpAddress() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            return localHost.getHostAddress();
        } catch (UnknownHostException e) {
            log.warn("Unable to determine local IP address, using default", e);
            return "127.0.0.1";
        }
    }

    /**
     * Format a monetary amount to 2 decimal places.
     * Example: 1000.0 -> "1000.00"
     *
     * @param amount The amount to format
     * @return Formatted amount string
     */
    public static String formatAmount(double amount) {
        return AMOUNT_FORMATTER.format(amount);
    }

    /**
     * Generate business name control text from business name.
     * Takes the first 4 characters of the business name, uppercase.
     *
     * @param businessName The full business name
     * @return 4-character business name control
     */
    public static String generateBusinessNameControl(String businessName) {
        if (businessName == null || businessName.isEmpty()) {
            return "UNKN";
        }
        // Remove non-alphanumeric characters and take first 4 chars
        String cleaned = businessName.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (cleaned.length() >= 4) {
            return cleaned.substring(0, 4);
        } else {
            // Pad with spaces if less than 4 characters
            return String.format("%-4s", cleaned);
        }
    }

    /**
     * Generate an 8-digit software ID.
     *
     * @return 8-digit software ID
     */
    public static String generateSoftwareId() {
        return String.format("%08d", RANDOM.nextInt(100000000));
    }

    /**
     * Generate a 6-digit EFIN (Electronic Filing Identification Number).
     *
     * @return 6-digit EFIN
     */
    public static String generateEFIN() {
        return String.format("%06d", RANDOM.nextInt(1000000));
    }

    /**
     * Format EIN by removing hyphens.
     * Example: "00-3000004" -> "003000004"
     *
     * @param ein The EIN with or without hyphens
     * @return EIN without hyphens
     */
    public static String formatEIN(String ein) {
        if (ein == null) {
            return null;
        }
        return ein.replace("-", "");
    }

    /**
     * Format quarter ending date for the given quarter and year.
     * Example: Q1 2026 -> "2026-03"
     *
     * @param quarter Quarter number (1-4)
     * @param year Year
     * @return Quarter ending date string (YYYY-MM format)
     */
    public static String formatQuarterEndingDate(int quarter, int year) {
        int month = quarter * 3; // Q1=3, Q2=6, Q3=9, Q4=12
        return String.format("%d-%02d", year, month);
    }

    /**
     * Validate that an amount string is properly formatted (2 decimal places).
     *
     * @param amountStr The amount string to validate
     * @return true if valid format, false otherwise
     */
    public static boolean isValidAmountFormat(String amountStr) {
        if (amountStr == null || amountStr.isEmpty()) {
            return false;
        }
        // Check if it matches pattern: digits.2decimals
        return amountStr.matches("\\d+\\.\\d{2}");
    }
}
