package com.example.tally.inboundsync.masterstock.service;

import com.example.tally.outboundsync.masterstock.entity.MasterStockItem;
import com.example.tally.outboundsync.masterstock.entity.MasterVendor;
import com.example.tally.outboundsync.masterstock.entity.MasterWarehouse;
import com.example.tally.outboundsync.masterstock.repository.MasterStockItemRepository;
import com.example.tally.outboundsync.masterstock.repository.MasterVendorRepository;
import com.example.tally.outboundsync.masterstock.repository.MasterWarehouseRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@RequiredArgsConstructor
public class MasterInboundSyncService {

    private final MasterVendorRepository vendorRepository;
    private final MasterStockItemRepository stockItemRepository;
    private final MasterWarehouseRepository warehouseRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${tally.base-url:http://localhost:9000}")
    private String tallyBaseUrl;

    @Value("${tally.company:Actify R&D}")
    private String companyName;

    public String pullVendors() throws Exception {
        JsonNode messages = exportFromTally("Ledgers");
        int count = 0;

        for (JsonNode node : messages) {
            if (!"Sundry Creditors".equalsIgnoreCase(node.path("parent").asText())) {
                continue;
            }

            String name = extractName(node);
            if (name.isEmpty()) continue;

            MasterVendor vendor = vendorRepository.findByCompanyNameIgnoreCase(name)
                    .orElseGet(MasterVendor::new);

            vendor.setCompanyName(name);
            vendor.setState(node.path("priorstatename").asText(null));
            vendor.setCountry(node.path("countryofresidence").asText(null));

            JsonNode gstDetails = node.path("ledgstregdetails");
            if (gstDetails.isArray() && gstDetails.size() > 0) {
                JsonNode latestGst = gstDetails.get(gstDetails.size() - 1);
                vendor.setGstin(latestGst.path("gstin").asText(null));
                vendor.setGstRegistrationType(latestGst.path("gstregistrationtype").asText(null));
            }

            vendor.setTallySyncStatus("SYNCED");
            vendorRepository.save(vendor);
            count++;
        }
        return "Pulled " + count + " vendors from Tally.";
    }

    public String pullStockItems() throws Exception {
        JsonNode messages = exportFromTally("Stock Items");
        int count = 0;

        for (JsonNode node : messages) {
            String name = extractName(node);
            if (name.isEmpty()) continue;

            MasterStockItem item = stockItemRepository.findByItemNameIgnoreCase(name)
                    .orElseGet(MasterStockItem::new);

            item.setItemName(name);
            item.setUnit(node.path("baseunits").asText("Nos"));

            String hsn = extractHsnCode(node);
            if (hsn != null) {
                item.setHsnCode(hsn);
            }

            String taxRate = extractTaxRate(node);
            if (taxRate != null) {
                item.setTaxRate(taxRate);
            }

            item.setTallySyncStatus("SYNCED");
            stockItemRepository.save(item);
            count++;
        }
        return "Pulled " + count + " stock items from Tally.";
    }

    public String pullWarehouses() throws Exception {
        JsonNode messages = exportFromTally("Godowns");
        int count = 0;

        for (JsonNode node : messages) {
            String name = extractName(node);
            if (name.isEmpty()) continue;

            MasterWarehouse warehouse = warehouseRepository.findByWarehouseNameIgnoreCase(name)
                    .orElseGet(MasterWarehouse::new);

            warehouse.setWarehouseName(name);

            warehouse.setLocationAddress(extractAddress(node.path("address")));
            warehouse.setTallySyncStatus("SYNCED");
            warehouseRepository.save(warehouse);
            count++;
        }
        return "Pulled " + count + " warehouses from Tally.";
    }

    private String extractName(JsonNode node) {
        String name = node.path("name").asText("");
        if (name.isEmpty()) {
            name = node.path("metadata").path("name").asText("");
        }
        return name.trim();
    }

    private JsonNode exportFromTally(String accountType) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode vars = root.putArray("static_variables");
        vars.addObject().put("name", "svExportFormat").put("value", "jsonex");
        vars.addObject().put("name", "svCurrentCompany").put("value", companyName);
        vars.addObject().put("name", "AccountType").put("value", accountType);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("version", "1");
        headers.set("tallyrequest", "Export");
        headers.set("type", "Data");
        headers.set("id", "List of Accounts");

        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(root), headers);
        String body = restTemplate.exchange(tallyBaseUrl, HttpMethod.POST, request, String.class).getBody();

        if (body == null || body.trim().isEmpty()) {
            throw new IllegalStateException("Tally returned an empty response for " + accountType);
        }

        JsonNode json = objectMapper.readTree(body);
        JsonNode[] candidates = new JsonNode[] {
                json.path("tallymessage"),
                json.path("data").path("tallymessage"),
                json.path("data").path("collection")
        };

        for (JsonNode candidate : candidates) {
            if (candidate.isArray() && candidate.size() > 0) {
                return candidate;
            }
        }

        throw new RuntimeException("RAW TALLY RESPONSE [" + accountType + "]: " + body);
    }
    private String extractAddress(JsonNode addressNode) {
        if (addressNode == null || addressNode.isMissingNode() || addressNode.isNull()) {
            return null;
        }
        if (addressNode.isTextual()) {
            String text = addressNode.asText().trim();
            return text.isEmpty() ? null : text;
        }
        if (addressNode.isArray()) {
            List<String> lines = new ArrayList<>();
            for (JsonNode element : addressNode) {
                if (element.isTextual()) {
                    String line = element.asText().trim();
                    if (!line.isEmpty()) {
                        lines.add(line);
                    }
                }
            }
            return lines.isEmpty() ? null : String.join(", ", lines);
        }
        return null;
    }
    private String extractHsnCode(JsonNode node) {
        // TallyPrime 3.0+: hsndetails -> hsncode
        JsonNode hsnDetails = node.path("hsndetails");
        if (hsnDetails.isArray() && hsnDetails.size() > 0) {
            JsonNode latest = hsnDetails.get(hsnDetails.size() - 1);
            String code = latest.path("hsncode").asText("").trim();
            if (!code.isEmpty()) return code;
        }

        // Legacy fallback: gstdetail -> hsncode
        JsonNode legacy = node.path("gstdetail");
        if (legacy.isArray() && legacy.size() > 0) {
            String code = legacy.get(legacy.size() - 1).path("hsncode").asText("").trim();
            if (!code.isEmpty()) return code;
        }

        return null;
    }

    private String extractTaxRate(JsonNode node) {
        JsonNode gstDetails = node.path("gstdetails");
        if (gstDetails.isArray() && gstDetails.size() > 0) {
            JsonNode latestGst = gstDetails.get(gstDetails.size() - 1);
            JsonNode statewise = latestGst.path("statewisedetails");
            if (statewise.isArray() && statewise.size() > 0) {
                JsonNode rateDetails = statewise.get(0).path("ratedetails");
                if (rateDetails.isArray()) {
                    for (JsonNode rate : rateDetails) {
                        String head = rate.path("gstratedutyhead").asText("");
                        if ("IGST".equalsIgnoreCase(head) || "Integrated Tax".equalsIgnoreCase(head)) {
                            String val = rate.path("gstrate").asText("").trim();
                            if (!val.isEmpty()) return val;
                        }
                    }
                }
            }
        }

        JsonNode legacy = node.path("gstdetail");
        if (legacy.isArray() && legacy.size() > 0) {
            String val = legacy.get(legacy.size() - 1).path("integratedtax").asText("").trim();
            if (!val.isEmpty()) return val;
        }

        return null;
    }
}