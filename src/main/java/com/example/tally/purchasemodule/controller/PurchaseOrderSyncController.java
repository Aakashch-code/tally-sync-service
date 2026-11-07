package com.example.tally.purchasemodule.controller;


import com.example.tally.purchasemodule.service.PurchaseOrderSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tally/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderSyncController {

    private final PurchaseOrderSyncService poSyncService;

    @PostMapping("/sync-pending")
    public ResponseEntity<String> syncPurchaseOrders() {
        try {
            String result = poSyncService.syncPendingPurchaseOrders();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to sync Purchase Orders: " + e.getMessage());
        }
    }
}