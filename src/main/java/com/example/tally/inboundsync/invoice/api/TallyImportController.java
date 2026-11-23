package com.example.tally.inboundsync.invoice.api;


import com.example.tally.inboundsync.invoice.service.TallyImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tally-import")
@RequiredArgsConstructor
public class TallyImportController {

    private final TallyImportService tallyImportService;

    @PostMapping("/sync-all")
    public ResponseEntity<String> syncFromTally() {
        try {
            tallyImportService.importCustomersAndInvoices();
            return ResponseEntity.ok("Successfully imported Ledgers and Vouchers from Tally to DB.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Import failed: " + e.getMessage());
        }
    }
}