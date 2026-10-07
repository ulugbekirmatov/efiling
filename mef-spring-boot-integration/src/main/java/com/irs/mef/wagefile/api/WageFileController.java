package com.irs.mef.wagefile.api;

import com.irs.mef.wagefile.WageFileService;
import com.irs.mef.wagefile.WageFileValidationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mef/wage-files")
public class WageFileController {

    private final WageFileService service;

    public WageFileController(WageFileService service) {
        this.service = service;
    }

    @PostMapping
    public WageFileResponse build(@RequestBody BuildWageFileRequest request) {
        return WageFileResponse.from(service.build(request.taxYear()));
    }

    @PostMapping("/{id}/receipt")
    public ResponseEntity<?> receipt(@PathVariable String id, @RequestBody WageFileReceiptRequest request) {
        try {
            String identifier = request == null ? null : request.wageFileIdentifier();
            return ResponseEntity.ok(WageFileResponse.from(service.recordReceipt(id, identifier)));
        } catch (WageFileValidationException ex) {
            return ResponseEntity.badRequest().body(new WageFileErrorResponse(ex.getMessage()));
        }
    }
}
