package com.example.tally.masterstock.controller;

import com.example.tally.masterstock.entity.MasterStockItem;
import com.example.tally.masterstock.entity.MasterVendor;

import com.example.tally.masterstock.repository.MasterStockItemRepository;
import com.example.tally.masterstock.service.MasterSyncService;
import com.example.tally.masterstock.repository.MasterVendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/tally/masters")
@RequiredArgsConstructor
public class MasterSyncController {

    private final MasterSyncService masterSyncService;
    private final MasterVendorRepository vendorRepository;
    private final MasterStockItemRepository stockItemRepository;

    @PostMapping("/sync-vendors")
    public ResponseEntity<String> syncPendingVendors() {
        List<MasterVendor> pendingVendors = vendorRepository.findAll().stream()
                .filter(v -> "PENDING".equalsIgnoreCase(v.getTallySyncStatus()))
                .collect(Collectors.toList());

        int successCount = 0;
        int failedCount = 0;

        for (MasterVendor vendor : pendingVendors) {
            try {
                masterSyncService.syncVendor(vendor);
                vendorRepository.save(vendor);
                successCount++;
            } catch (Exception e) {
                vendor.setTallySyncStatus("FAILED");
                vendorRepository.save(vendor);
                failedCount++;
                System.err.println("Failed to sync vendor " + vendor.getCompanyName() + ": " + e.getMessage());
            }
        }

        return ResponseEntity.ok("Vendor Sync Complete. Success: " + successCount + ", Failed: " + failedCount);
    }

    @PostMapping("/sync-stock-items")
    public ResponseEntity<String> syncPendingStockItems() {
        List<MasterStockItem> pendingItems = stockItemRepository.findAll().stream()
                .filter(i -> "PENDING".equalsIgnoreCase(i.getTallySyncStatus()))
                .collect(Collectors.toList());

        int successCount = 0;
        int failedCount = 0;

        for (MasterStockItem item : pendingItems) {
            try {
                masterSyncService.syncStockItem(item);
                stockItemRepository.save(item);
                successCount++;
            } catch (Exception e) {
                item.setTallySyncStatus("FAILED");
                stockItemRepository.save(item);
                failedCount++;
                System.err.println("Failed to sync stock item " + item.getItemName() + ": " + e.getMessage());
            }
        }

        return ResponseEntity.ok("Stock Item Sync Complete. Success: " + successCount + ", Failed: " + failedCount);
    }
}