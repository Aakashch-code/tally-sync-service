package com.example.tally.outboundsync.api;

import com.example.tally.outboundsync.service.InvoiceSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tally")
@RequiredArgsConstructor
public class InvoiceSyncController {

    private final InvoiceSyncService invoiceSyncService;

    @PostMapping("/sync-invoices")
    public ResponseEntity<String> syncInvoices() {
        return ResponseEntity.ok(
                invoiceSyncService.syncPendingInvoices()
        );
    }

}