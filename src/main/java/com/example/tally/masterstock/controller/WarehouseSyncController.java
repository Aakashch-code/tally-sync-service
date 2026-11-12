package com.example.tally.masterstock.controller;

import com.example.tally.masterstock.service.WarehouseSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tally/warehouses")
@RequiredArgsConstructor
public class WarehouseSyncController {

    private final WarehouseSyncService warehouseSyncService;

    @PostMapping("/sync")
    public ResponseEntity<String> syncPendingWarehouses() {
        try {
            String result = warehouseSyncService.syncPendingWarehouses();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Warehouse sync failed: " + e.getMessage());
        }
    }
}