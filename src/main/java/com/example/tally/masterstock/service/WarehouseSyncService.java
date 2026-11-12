package com.example.tally.masterstock.service;

import com.example.tally.masterstock.entity.MasterWarehouse;
import com.example.tally.masterstock.repository.MasterWarehouseRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WarehouseSyncService {

    private final MasterWarehouseRepository warehouseRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${tally.base-url:http://localhost:9000}")
    private String tallyBaseUrl;

    public String syncPendingWarehouses() {
        List<MasterWarehouse> pendingWarehouses = warehouseRepository.findByTallySyncStatus("PENDING");
        int successCount = 0;
        int failedCount = 0;

        for (MasterWarehouse warehouse : pendingWarehouses) {
            try {
                ObjectNode payload = buildWarehouseJson(warehouse);
                sendToTally(payload);

                warehouse.setTallySyncStatus("SYNCED");
                successCount++;

            } catch (Exception e) {
                warehouse.setTallySyncStatus("FAILED");
                failedCount++;
                System.err.println("Warehouse " + warehouse.getWarehouseName() + " failed: " + e.getMessage());
            }
            warehouseRepository.save(warehouse);
        }

        return "Warehouse Master Sync completed. Total: " + pendingWarehouses.size()
                + ", successful: " + successCount
                + ", failed: " + failedCount;
    }

    private ObjectNode buildWarehouseJson(MasterWarehouse warehouse) {
        ObjectNode root = objectMapper.createObjectNode();

        ArrayNode staticVariables = root.putArray("static_variables");
        addStaticVariable(staticVariables, "svVchImportFormat", "jsonex");
        addStaticVariable(staticVariables, "svCurrentCompany", "Actify R&D");

        ArrayNode tallyMessages = root.putArray("tallymessage");
        ObjectNode masterEntry = tallyMessages.addObject();

        ObjectNode metadata = masterEntry.putObject("metadata");
        metadata.put("type", "Master");
        metadata.put("vchtype", "Godown");
        metadata.put("action", "Create");

        masterEntry.put("name", warehouse.getWarehouseName());
        masterEntry.put("parent", "Primary");


         masterEntry.put("address", warehouse.getLocationAddress());

        return root;
    }

    private void addStaticVariable(ArrayNode variables, String name, String value) {
        ObjectNode variable = variables.addObject();
        variable.put("name", name);
        variable.put("value", value);
    }

    private void sendToTally(ObjectNode payload) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("version", "1");
        headers.set("tallyrequest", "Import");
        headers.set("type", "Data");
        headers.set("id", "All Masters");

        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(payload), headers);
        ResponseEntity<String> response = restTemplate.exchange(tallyBaseUrl, HttpMethod.POST, request, String.class);

        if (response.getBody() == null || response.getBody().trim().isEmpty()) {
            throw new IllegalStateException("Tally returned an empty response");
        }

        JsonNode jsonResponse = objectMapper.readTree(response.getBody());
        JsonNode importResult = jsonResponse.path("data").path("import_result");

        if (!"1".equals(jsonResponse.path("status").asText()) || importResult.path("errors").asInt() > 0) {
            throw new RuntimeException("Tally sync failed: " + importResult.toPrettyString());
        }
    }
}