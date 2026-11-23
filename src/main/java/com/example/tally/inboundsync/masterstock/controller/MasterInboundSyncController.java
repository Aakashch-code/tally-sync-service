package com.example.tally.inboundsync.masterstock.controller;

import com.example.tally.inboundsync.masterstock.service.MasterInboundSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tally/inbound/masters")
@RequiredArgsConstructor
public class MasterInboundSyncController {

    private final MasterInboundSyncService inboundSyncService;

    @PostMapping("/pull-vendors")
    public ResponseEntity<String> pullVendors() throws Exception {
        return ResponseEntity.ok(inboundSyncService.pullVendors());
    }

    @PostMapping("/pull-stock-items")
    public ResponseEntity<String> pullStockItems() throws Exception {
        return ResponseEntity.ok(inboundSyncService.pullStockItems());
    }

    @PostMapping("/pull-warehouses")
    public ResponseEntity<String> pullWarehouses() throws Exception {
        return ResponseEntity.ok(inboundSyncService.pullWarehouses());
    }
}