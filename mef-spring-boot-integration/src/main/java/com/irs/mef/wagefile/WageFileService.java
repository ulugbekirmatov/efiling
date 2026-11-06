package com.irs.mef.wagefile;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class WageFileService {

    private final WageYearSource source;
    private final Efw2Writer writer;
    private final Efw2Validator validator;
    private final WageFileStore store;

    public WageFileService(WageYearSource source, Efw2Writer writer, Efw2Validator validator, WageFileStore store) {
        this.source = source;
        this.writer = writer;
        this.validator = validator;
        this.store = store;
    }

    public WageFileRun build(int taxYear) {
        WageYear year = source.load(taxYear);
        String text = writer.write(year);
        List<String> findings = validator.validate(text);
        if (!findings.isEmpty()) {
            throw new WageFileValidationException(String.join("; ", findings));
        }
        List<String> eins = year.employers().stream().map(EmployerWage::ein).toList();
        return store.create(taxYear, eins, text.getBytes(StandardCharsets.US_ASCII), null);
    }

    public WageFileRun recordReceipt(String id, String wageFileIdentifier) {
        if (wageFileIdentifier == null || wageFileIdentifier.length() != 6) {
            throw new WageFileValidationException("wageFileIdentifier must be 6 characters");
        }
        return store.recordReceipt(id, wageFileIdentifier);
    }

    public WageFileRun resubmit(String id) {
        WageFileRun original = store.read(id);
        String identifier = original.wageFileIdentifier();
        if (identifier == null || identifier.length() != 6) {
            throw new WageFileValidationException("resubmit requires a stored 6-character Wage File Identifier");
        }
        byte[] saved = store.readBytes(id);
        byte[] patched = writer.markResubmit(saved, identifier);
        List<String> findings = validator.validate(new String(patched, StandardCharsets.US_ASCII));
        if (!findings.isEmpty()) {
            throw new WageFileValidationException(String.join("; ", findings));
        }
        return store.create(original.taxYear(), original.employerEins(), patched, original.id());
    }
}
