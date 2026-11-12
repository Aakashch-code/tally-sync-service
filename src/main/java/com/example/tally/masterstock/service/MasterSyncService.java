package com.example.tally.masterstock.service;

import com.example.tally.masterstock.entity.MasterStockItem;
import com.example.tally.masterstock.entity.MasterVendor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class MasterSyncService {

    private static final String GST_APPLICABLE_FROM = "20260401";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${tally.base-url:http://localhost:9000}")
    private String tallyBaseUrl;

    @Value("${tally.company:Actify R&D}")
    private String companyName;

    public void syncVendor(MasterVendor vendor) throws Exception {
        ObjectNode root = newPayload();
        ObjectNode ledger = addMaster(root, "Ledger", vendor.getCompanyName());

        ledger.put("parent", "Sundry Creditors");
        ledger.put("isbillwiseon", true);
        ledger.put("priorstatename", vendor.getState());
        ledger.put("countryofresidence", vendor.getCountry());

        if (vendor.getGstin() != null) {
            ledger.putArray("ledgstregdetails").addObject()
                    .put("applicablefrom", GST_APPLICABLE_FROM)
                    .put("gstregistrationtype", vendor.getGstRegistrationType())
                    .put("gstin", vendor.getGstin());
        }

        sendToTally(root);
        vendor.setTallySyncStatus("SYNCED");
    }

    public void syncStockItem(MasterStockItem item) throws Exception {
        ObjectNode root = newPayload();
        ObjectNode stockItem = addMaster(root, "Stock Item", item.getItemName());

        stockItem.put("parent", "");
        stockItem.put("baseunits", item.getUnit());

        if (item.getTaxRate() != null && !item.getTaxRate().isEmpty()) {
            stockItem.put("isgstapplicable", "Applicable");
            stockItem.putArray("gstdetail").addObject()
                    .put("applicablefrom", GST_APPLICABLE_FROM)
                    .put("hsncode", item.getHsnCode())
                    .put("taxability", "Taxable")
                    .put("integratedtax", item.getTaxRate());
        }

        sendToTally(root);
        item.setTallySyncStatus("SYNCED");
    }

    private ObjectNode newPayload() {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode vars = root.putArray("static_variables");
        addStaticVariable(vars, "svMstImportFormat", "jsonex");
        addStaticVariable(vars, "svCurrentCompany", companyName);
        return root;
    }

    private ObjectNode addMaster(ObjectNode root, String type, String name) {
        ObjectNode master = root.withArray("tallymessage").addObject();
        master.putObject("metadata")
                .put("type", type)
                .put("action", "Create")
                .put("name", name);
        master.put("name", name);
        return master;
    }

    private void addStaticVariable(ArrayNode variables, String name, String value) {
        variables.addObject().put("name", name).put("value", value);
    }

    private void sendToTally(ObjectNode payload) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("version", "1");
        headers.set("tallyrequest", "Import");
        headers.set("type", "Data");
        headers.set("id", "All Masters");

        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(payload), headers);
        String body = restTemplate.exchange(tallyBaseUrl, HttpMethod.POST, request, String.class).getBody();

        if (body == null || body.trim().isEmpty()) {
            throw new IllegalStateException("Tally returned an empty response");
        }

        JsonNode json = objectMapper.readTree(body);
        JsonNode result = json.path("data").path("import_result");

        if (!"1".equals(json.path("status").asText())
                || result.path("errors").asInt() > 0
                || result.path("exceptions").asInt() > 0) {
            throw new RuntimeException("Tally Master sync failed: " + result.toPrettyString());
        }
    }
}