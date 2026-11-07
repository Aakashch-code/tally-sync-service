package com.example.tally.outboundsync.infrastructure.tally;

import com.example.tally.outboundsync.infrastructure.tally.dto.InvoiceRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Service
public class TallyInvoiceService {

    @Value("${tally.base-url:http://localhost:9000}")
    private String tallyBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    public TallyInvoiceService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode createSalesInvoice(InvoiceRequest invoice) throws Exception {
        BigDecimal amount = BigDecimal.valueOf(invoice.getAmount()).setScale(2, java.math.RoundingMode.HALF_UP);

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Invoice amount must be greater than zero");
        }

        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode staticVariables = root.putArray("static_variables");
        addStaticVariable(staticVariables, "svVchImportFormat", "jsonex");

        addStaticVariable(staticVariables, "svCurrentCompany", "Actify R&D");

        ArrayNode tallyMessages = root.putArray("tallymessage");
        ObjectNode voucher = tallyMessages.addObject();

        ObjectNode metadata = voucher.putObject("metadata");
        metadata.put("type", "Voucher");
        metadata.put("vchtype", "Sales");
        metadata.put("action", "Create");
        metadata.put("objview", "Accounting Voucher View");

        voucher.put("date", invoice.getDate());
        voucher.put("vouchertypename", "Sales");
        voucher.put("vouchernumber", invoice.getVoucherNumber());
        voucher.put("partyledgername", invoice.getPartyLedgerName());
        voucher.put("partyname", invoice.getPartyLedgerName());
        voucher.put("persistedview", "Accounting Voucher View");
        ArrayNode ledgerEntries = voucher.putArray("ledgerentries");

        ObjectNode customerEntry = ledgerEntries.addObject();
        customerEntry.put("ledgername", invoice.getPartyLedgerName());
        customerEntry.put("isdeemedpositive", true);
        customerEntry.put("ispartyledger", true);
        customerEntry.put("amount", amount.negate().toPlainString());

        ArrayNode billAllocations = customerEntry.putArray("billallocations");
        ObjectNode bill = billAllocations.addObject();
        bill.put("name", invoice.getVoucherNumber()); // FIXED: Was hardcoded to "INV-" + date
        bill.put("billtype", "New Ref");
        bill.put("amount", amount.negate().toPlainString());

        ObjectNode salesEntry = ledgerEntries.addObject();
        salesEntry.put("ledgername", invoice.getSalesLedgerName());
        salesEntry.put("isdeemedpositive", false);
        salesEntry.put("ispartyledger", false);
        salesEntry.put("amount", amount.toPlainString());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("version", "1");
        headers.set("tallyrequest", "Import");
        headers.set("type", "Data");
        headers.set("id", "Vouchers");

        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(root), headers);

        ResponseEntity<String> response = restTemplate.exchange(tallyBaseUrl, HttpMethod.POST, request, String.class);

        if (response.getBody() == null || response.getBody().trim().isEmpty()) {
            throw new IllegalStateException("Tally returned an empty response");
        }

        JsonNode jsonResponse = parseTallyResponse(response.getBody());
        JsonNode importResult = jsonResponse.path("data").path("import_result");

        if (!"1".equals(jsonResponse.path("status").asText())) {
            throw new RuntimeException("Tally sync failed: " + response.getBody());
        }

        int errors = importResult.path("errors").asInt();
        int exceptions = importResult.path("exceptions").asInt();

        if (errors > 0 || exceptions > 0) {
            throw new RuntimeException(
                    "Tally import had errors or exceptions: " + importResult.toPrettyString()
            );
        }

        return jsonResponse;
    }

    private void addStaticVariable(ArrayNode variables, String name, String value) {
        ObjectNode variable = variables.addObject();
        variable.put("name", name);
        variable.put("value", value);
    }

    private JsonNode parseTallyResponse(String body) throws Exception {

        String normalized = body.replaceAll(
                "(?m)(\"vchnumber\"\\s*:\\s*)([A-Za-z0-9_-]+)(\\s*[,}])",
                "$1\"$2\"$3"
        );

        try {
            return objectMapper.readTree(normalized);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(
                    "Tally returned invalid JSON. Raw response: " + body, e
            );
        }
    }
}