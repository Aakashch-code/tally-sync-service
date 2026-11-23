package com.example.tally.inboundsync.purchasemodule.controller;

import com.example.tally.inboundsync.purchasemodule.service.PurchaseOrderInboundSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tally/inbound/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderInboundSyncController {

    private final PurchaseOrderInboundSyncService inboundSyncService;

    @PostMapping("/pull")
    public ResponseEntity<String> pullPurchaseOrders(
            @RequestParam String fromDate,
            @RequestParam String toDate) throws Exception {
        return ResponseEntity.ok(inboundSyncService.pullPurchaseOrders(fromDate, toDate));
    }
}