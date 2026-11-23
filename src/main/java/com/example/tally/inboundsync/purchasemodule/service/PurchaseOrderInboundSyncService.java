package com.example.tally.inboundsync.purchasemodule.service;

import com.example.tally.outboundsync.masterstock.entity.MasterStockItem;
import com.example.tally.outboundsync.masterstock.entity.MasterVendor;
import com.example.tally.outboundsync.masterstock.entity.MasterWarehouse;
import com.example.tally.outboundsync.masterstock.repository.MasterStockItemRepository;
import com.example.tally.outboundsync.masterstock.repository.MasterVendorRepository;
import com.example.tally.outboundsync.masterstock.repository.MasterWarehouseRepository;
import com.example.tally.outboundsync.purchasemodule.entity.MasterPurchaseOrder;
import com.example.tally.outboundsync.purchasemodule.repository.MasterPurchaseOrderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class PurchaseOrderInboundSyncService {

    private static final DateTimeFormatter TALLY_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MasterPurchaseOrderRepository poRepository;
    private final MasterVendorRepository vendorRepository;
    private final MasterStockItemRepository stockItemRepository;
    private final MasterWarehouseRepository warehouseRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${tally.base-url:http://localhost:9000}")
    private String tallyBaseUrl;

    @Value("${tally.company:Actify R&D}")
    private String companyName;

    public String pullPurchaseOrders(String fromDateYyyyMmDd, String toDateYyyyMmDd) throws Exception {
        JsonNode messages = exportVouchers(fromDateYyyyMmDd, toDateYyyyMmDd);
        int synced = 0;
        int skipped = 0;

        for (JsonNode voucher : messages) {
            if (!"Purchase Order".equalsIgnoreCase(voucher.path("vouchertypename").asText())) {
                continue;
            }

            String poNumber = voucher.path("vouchernumber").asText();
            String partyName = voucher.path("partyledgername").asText();

            MasterVendor vendor = vendorRepository.findByCompanyNameIgnoreCase(partyName).orElse(null);
            if (vendor == null) {
                System.err.println("SKIPPED PO " + poNumber + " - Vendor not in DB: [" + partyName + "]");
                skipped++;
                continue;
            }

            JsonNode inventoryEntries = voucher.path("inventoryentries");
            if (!inventoryEntries.isArray() || inventoryEntries.size() == 0) {
                inventoryEntries = voucher.path("allinventoryentries");
            }
            if (!inventoryEntries.isArray() || inventoryEntries.size() == 0) {
                System.err.println("SKIPPED PO " + poNumber + " - No inventory entries found. Raw keys: " + voucher.fieldNames());
                skipped++;
                continue;
            }

            JsonNode itemNode = inventoryEntries.get(0);
            String itemName = itemNode.path("stockitemname").asText();
            MasterStockItem stockItem = stockItemRepository.findByItemNameIgnoreCase(itemName).orElse(null);
            if (stockItem == null) {
                System.err.println("SKIPPED PO " + poNumber + " - Stock item not in DB: [" + itemName + "]");
                skipped++;
                continue;
            }

            String godownName = itemNode.path("batchallocations").path(0).path("godownname").asText("Main Location");
            MasterWarehouse warehouse = warehouseRepository.findByWarehouseNameIgnoreCase(godownName).orElse(null);

            MasterPurchaseOrder po = poRepository.findByPoNumber(poNumber).orElseGet(MasterPurchaseOrder::new);
            po.setPoNumber(poNumber);
            po.setPoDate(LocalDate.parse(voucher.path("date").asText(), TALLY_DATE));
            po.setVendorId(vendor.getId());
            po.setStockItemId(stockItem.getId());
            if (warehouse != null) {
                po.setWarehouseId(warehouse.getId());
            }

            po.setQuantity(parseQuantity(itemNode.path("billedqty").asText("0")));
            po.setRatePerItem(parseRate(itemNode.path("rate").asText("0")));
            po.setBaseAmount(parseAbsAmount(itemNode.path("amount").asText("0")));

            BigDecimal cgst = BigDecimal.ZERO;
            BigDecimal sgst = BigDecimal.ZERO;
            BigDecimal igst = BigDecimal.ZERO;
            BigDecimal total = BigDecimal.ZERO;

            for (JsonNode ledger : voucher.path("ledgerentries")) {
                String ledgerName = ledger.path("ledgername").asText();
                BigDecimal amt = parseAbsAmount(ledger.path("amount").asText("0"));

                if (partyName.equalsIgnoreCase(ledgerName)) {
                    total = amt;
                } else if ("Input CGST".equalsIgnoreCase(ledgerName)) {
                    cgst = amt;
                } else if ("Input SGST".equalsIgnoreCase(ledgerName)) {
                    sgst = amt;
                } else if ("Input IGST".equalsIgnoreCase(ledgerName)) {
                    igst = amt;
                }
            }

            po.setCgstAmount(cgst);
            po.setSgstAmount(sgst);
            po.setIgstAmount(igst);
            po.setTotalAmount(total);
            po.setTallySyncStatus("SYNCED");

            poRepository.save(po);
            synced++;
        }

        return "PO Inbound Sync complete. Synced: " + synced + ", Skipped (missing masters): " + skipped;
    }

    private JsonNode exportVouchers(String fromDate, String toDate) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode vars = root.putArray("static_variables");
        vars.addObject().put("name", "svExportFormat").put("value", "jsonex");
        vars.addObject().put("name", "svCurrentCompany").put("value", companyName);
        vars.addObject().put("name", "svFromDate").put("value", fromDate);
        vars.addObject().put("name", "svToDate").put("value", toDate);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("version", "1");
        headers.set("tallyrequest", "Export");
        headers.set("type", "Data");
        headers.set("id", "Day Book");

        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(root), headers);
        String body = restTemplate.exchange(tallyBaseUrl, HttpMethod.POST, request, String.class).getBody();

        if (body == null || body.trim().isEmpty()) {
            throw new IllegalStateException("Tally returned an empty Day Book response");
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

        throw new RuntimeException("RAW DAY BOOK RESPONSE: " + body);
    }

    private Integer parseQuantity(String rawQty) {
        String numeric = rawQty.trim().split("\\s+")[0];
        return numeric.isEmpty() ? 0 : new BigDecimal(numeric).intValue();
    }

    private BigDecimal parseRate(String rawRate) {
        String numeric = rawRate.trim().split("/")[0];
        return numeric.isEmpty() ? BigDecimal.ZERO : new BigDecimal(numeric);
    }

    private BigDecimal parseAbsAmount(String rawAmount) {
        return rawAmount.trim().isEmpty() ? BigDecimal.ZERO : new BigDecimal(rawAmount.trim()).abs();
    }
}