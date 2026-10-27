package com.irs.mef.web;

import com.irs.mef.filing.Ein;
import com.irs.mef.filing.FormType;
import com.irs.mef.filing.SubmitCommand;
import com.irs.mef.filing.TaxPeriod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Wire request. Strings and primitives only; converted to domain types in {@link #toCommand}
 * and never seen past the controller.
 *
 * Deliberately ABSENT: submissionId (service-minted), efin (config), submissionFilePath
 * (the legacy arbitrary-file-read hole), productionMode (environment is config, not caller input).
 */
public record FilingRequest(

        @NotBlank @Size(max = 64)
        String clientId,

        @NotBlank @Pattern(regexp = "[0-9]{9}", message = "clientEin must be exactly 9 digits, no dash")
        String clientEin,

        @NotBlank
        String formType,

        @NotNull
        LocalDate taxPeriodBegin,

        @NotNull
        LocalDate taxPeriodEnd,

        /* The return document itself — never a server path. */
        @NotBlank
        String returnXml,

        boolean allowDuplicatePeriod) {

    /** The single wire-to-domain crossing. After this, every value is a validated domain type. */
    public SubmitCommand toCommand(String idempotencyKey) {
        return new SubmitCommand(
                idempotencyKey,
                clientId,
                new Ein(clientEin),
                FormType.parse(formType),
                new TaxPeriod(taxPeriodBegin, taxPeriodEnd),
                returnXml,
                allowDuplicatePeriod);
    }
}
