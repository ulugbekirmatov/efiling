package com.irs.mef.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for acknowledgment operations.
 *
 * <p><b>Important Data Caveats from IRS:</b></p>
 * <ul>
 *   <li><b>EFIN may be "999999"</b> - IRS returns this placeholder when EFIN is unknown or unavailable</li>
 *   <li><b>TIN may be null</b> - IRS does not always populate the Taxpayer Identification Number</li>
 *   <li><b>EIN is the primary tax identifier</b> - Use this for employer identification</li>
 *   <li><b>Financial amounts may be null</b> - Not all return types include financial data</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AckResponse {

    /**
     * Acknowledgment ID
     */
    private String ackId;

    /**
     * Related submission ID
     */
    private String submissionId;

    /**
     * Acknowledgment type (e.g., "Accept", "Reject", "Partial")
     */
    private String ackType;

    /**
     * Timestamp of acknowledgment
     */
    private String timestamp;

    /**
     * Acknowledgment file path (if saved locally)
     */
    private String ackFilePath;

    /**
     * Error codes (for rejections)
     */
    private List<String> errorCodes;

    /**
     * Error messages (for rejections)
     */
    private List<String> errorMessages;

    /**
     * Additional acknowledgment details
     */
    private String details;

    // ========== Tax Identifiers ==========

    /**
     * Electronic Filing Identification Number (EFIN).
     * May be "999999" if IRS doesn't have the EFIN on record.
     */
    private String efin;

    /**
     * Employer Identification Number (EIN) - primary tax identifier.
     * Most reliable identifier for employer returns.
     */
    private String ein;

    /**
     * Taxpayer Identification Number (TIN).
     * May be null - IRS doesn't always populate this field.
     */
    private String tin;

    // ========== Financial Amounts ==========

    /**
     * Taxable income amount from the return.
     */
    private Long taxableIncomeAmt;

    /**
     * Total tax amount calculated.
     */
    private Long totalTaxAmt;

    /**
     * Balance due to IRS.
     */
    private Long balanceDueAmt;

    /**
     * Expected refund amount from IRS.
     */
    private Long expectedRefundAmt;

    /**
     * Net income or loss amount.
     */
    private Long netIncomeLossAmt;

    // ========== Form Details ==========

    /**
     * Tax year (e.g., "2025").
     */
    private String taxYear;

    /**
     * Submission type / form name (e.g., "941", "1040", "1120").
     */
    private String submissionType;

    /**
     * Extended submission category code.
     * Values: EMPL, CORP, IND, EO, PART, EXCISE, ESTRST, GIFT, UNKN
     */
    private String submissionCategoryCode;

    // ========== Timestamps ==========

    /**
     * Electronic postmark timestamp (official filing timestamp).
     */
    private String electronicPostmarkTs;

    /**
     * Date IRS received the submission.
     */
    private String irsReceivedDate;

    /**
     * Tax period end date.
     */
    private String taxPeriodEndDate;

    // ========== IRS Identifiers ==========

    /**
     * IRS-assigned submission ID (may differ from transmitter submission ID).
     */
    private String irsSubmissionId;

    /**
     * Receipt ID (acknowledgment identifier).
     */
    private String receiptId;

    // ========== Additional Fields ==========

    /**
     * Payment request received code.
     */
    private String paymentRequestCode;

    /**
     * Whether validation errors were contained in the acknowledgment.
     */
    private Boolean hasValidationErrors;

    /**
     * Whether validation alerts were contained in the acknowledgment.
     */
    private Boolean hasValidationAlerts;

    /**
     * Response from GetNewAcks containing multiple acknowledgments
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AckListResponse {
        private List<AckResponse> acknowledgments;
        private int totalCount;
        private String message;
    }
}
