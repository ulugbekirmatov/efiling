package com.irs.mef.newsend.api;

import com.irs.mef.newsend.domain.Ein;
import com.irs.mef.newsend.domain.FormType;
import com.irs.mef.newsend.domain.NewSendSubmitCommand;
import com.irs.mef.newsend.domain.TaxPeriod;
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
public record NewSendSubmitRequest(

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
    public NewSendSubmitCommand toCommand(String idempotencyKey) {
        return new NewSendSubmitCommand(
                idempotencyKey,
                clientId,
                new Ein(clientEin),
                FormType.parse(formType),
                new TaxPeriod(taxPeriodBegin, taxPeriodEnd),
                returnXml,
                allowDuplicatePeriod);
    }
}
