package com.example.tally.purchasemodule.service;

import com.example.tally.masterstock.entity.MasterStockItem;
import com.example.tally.masterstock.entity.MasterVendor;
import com.example.tally.masterstock.entity.MasterWarehouse;
import com.example.tally.masterstock.repository.MasterStockItemRepository;
import com.example.tally.masterstock.repository.MasterVendorRepository;
import com.example.tally.masterstock.repository.MasterWarehouseRepository;
import com.example.tally.purchasemodule.entity.MasterPurchaseOrder;
import com.example.tally.purchasemodule.repository.MasterPurchaseOrderRepository;
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
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PurchaseOrderSyncService {

    private static final String COMPANY_STATE = "Maharashtra";
    private static final DateTimeFormatter TALLY_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DUE_DATE = DateTimeFormatter.ofPattern("d-MMM-yy");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWO = new BigDecimal("2");

    private final MasterPurchaseOrderRepository poRepository;
    private final MasterVendorRepository vendorRepository;
    private final MasterStockItemRepository stockItemRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final MasterWarehouseRepository warehouseRepository;

    @Value("${tally.base-url:http://localhost:9000}")
    private String tallyBaseUrl;

    public String syncPendingPurchaseOrders() {
        List<MasterPurchaseOrder> pendingPOs = poRepository.findByTallySyncStatus("PENDING");
        int success = 0;

        for (MasterPurchaseOrder po : pendingPOs) {
            try {
                MasterVendor vendor = vendorRepository.findById(po.getVendorId())
                        .orElseThrow(() -> new IllegalStateException("Vendor not found: " + po.getVendorId()));
                MasterStockItem stockItem = stockItemRepository.findById(po.getStockItemId())
                        .orElseThrow(() -> new IllegalStateException("Stock Item not found: " + po.getStockItemId()));

                MasterWarehouse warehouse = warehouseRepository.findById(po.getWarehouseId())
                        .orElseThrow(() -> new IllegalStateException("Warehouse not found"));

                calculateAndApplyTaxes(po, vendor, stockItem);
                sendToTally(buildPurchaseOrderJson(po, vendor, stockItem,warehouse));

                po.setTallySyncStatus("SYNCED");
                success++;
            } catch (Exception e) {
                po.setTallySyncStatus("FAILED");
                System.err.println("PO " + po.getPoNumber() + " failed: " + e.getMessage());
            }
            poRepository.save(po);
        }

        return "Purchase Orders Sync completed. Total: " + pendingPOs.size()
                + ", successful: " + success
                + ", failed: " + (pendingPOs.size() - success);
    }

    private ObjectNode buildPurchaseOrderJson(MasterPurchaseOrder po, MasterVendor vendor, MasterStockItem stockItem, MasterWarehouse warehouse) {
        ObjectNode root = objectMapper.createObjectNode();

        ArrayNode vars = root.putArray("static_variables");
        addStaticVariable(vars, "svVchImportFormat", "jsonex");
        addStaticVariable(vars, "svCurrentCompany", "Actify R&D");

        ObjectNode voucher = root.putArray("tallymessage").addObject();
        voucher.putObject("metadata")
                .put("type", "Voucher")
                .put("vchtype", "Purchase Order")
                .put("action", "Create")
                .put("objview", "Invoice Voucher View");

        String date = po.getPoDate().format(TALLY_DATE);
        String dueDate = po.getPoDate().format(DUE_DATE);
        String party = vendor.getCompanyName();
        String poNumber = po.getPoNumber();

        for (String field : Arrays.asList("date", "effectivedate", "vchstatusdate")) {
            voucher.put(field, date);
        }
        voucher.put("vouchertypename", "Purchase Order");
        voucher.put("vouchernumber", poNumber);
        voucher.put("reference", poNumber);
        voucher.put("partyledgername", party);
        voucher.put("partyname", party);
        voucher.put("persistedview", "Invoice Voucher View");

        ArrayNode ledgerEntries = voucher.putArray("ledgerentries");
        addLedger(ledgerEntries, party, false, po.getTotalAmount().toPlainString())
                .put("ispartyledger", true);

        Map<String, BigDecimal> taxes = new LinkedHashMap<>();
        taxes.put("Input CGST", po.getCgstAmount());
        taxes.put("Input SGST", po.getSgstAmount());
        taxes.put("Input IGST", po.getIgstAmount());
        taxes.forEach((name, amount) -> {
            if (amount != null && amount.signum() > 0) {
                addLedger(ledgerEntries, name, true, amount.negate().toPlainString())
                        .put("ispartyledger", false);
            }
        });

        String unit = stockItem.getUnit();
        String qty = po.getQuantity() + " " + unit;
        String negBase = po.getBaseAmount().negate().toPlainString();

        ObjectNode item = voucher.putArray("inventoryentries").addObject();
        item.put("stockitemname", stockItem.getItemName());
        item.put("isdeemedpositive", true);
        item.put("rate", po.getRatePerItem() + "/" + unit);
        item.put("amount", negBase);
        item.put("actualqty", qty);
        item.put("billedqty", qty);
        item.put("orderduedate", dueDate);

        item.putArray("batchallocations").addObject()
                .put("godownname", "Main Location")
                .put("batchname", "Primary Batch")
                .put("orderno", poNumber)
                .put("orderduedate", dueDate)
                .put("amount", negBase)
                .put("actualqty", qty)
                .put("billedqty", qty);

        addLedger(item.putArray("accountingallocations"), "Purchase A/c", true, negBase);

        return root;
    }

    private ObjectNode addLedger(ArrayNode array, String name, boolean deemedPositive, String amount) {
        return array.addObject()
                .put("ledgername", name)
                .put("isdeemedpositive", deemedPositive)
                .put("amount", amount);
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
        headers.set("id", "Vouchers");

        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(payload), headers);
        String body = restTemplate.exchange(tallyBaseUrl, HttpMethod.POST, request, String.class).getBody();

        if (body == null || body.trim().isEmpty()) {
            throw new IllegalStateException("Tally returned an empty response");
        }

        String normalized = body.replaceAll("(?m)(\"vchnumber\"\\s*:\\s*)([A-Za-z0-9_-]+)(\\s*[,}])", "$1\"$2\"$3");
        JsonNode json = objectMapper.readTree(normalized);
        JsonNode result = json.path("data").path("import_result");

        if (!"1".equals(json.path("status").asText())
                || result.path("errors").asInt() > 0
                || result.path("exceptions").asInt() > 0) {
            throw new RuntimeException("Tally sync failed: " + result.toPrettyString());
        }
    }

    private void calculateAndApplyTaxes(MasterPurchaseOrder po, MasterVendor vendor, MasterStockItem stockItem) {
        BigDecimal baseAmount = po.getRatePerItem().multiply(new BigDecimal(po.getQuantity()));
        BigDecimal totalTax = baseAmount.multiply(new BigDecimal(stockItem.getTaxRate())).divide(HUNDRED);
        boolean intraState = COMPANY_STATE.equalsIgnoreCase(vendor.getState());
        BigDecimal halfTax = intraState ? totalTax.divide(TWO) : BigDecimal.ZERO;

        po.setBaseAmount(baseAmount);
        po.setCgstAmount(halfTax);
        po.setSgstAmount(halfTax);
        po.setIgstAmount(intraState ? BigDecimal.ZERO : totalTax);
        po.setTotalAmount(baseAmount.add(totalTax));
    }
}