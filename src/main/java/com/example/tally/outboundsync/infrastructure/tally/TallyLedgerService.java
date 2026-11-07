
package com.example.tally.outboundsync.infrastructure.tally;

import com.example.tally.outboundsync.domain.model.MasterCustomer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class TallyLedgerService {

    @Value("${tally.base-url:http://localhost:9000}")
    private String tallyBaseUrl;

    @Value("${tally.company:Actify R&D}")
    private String companyName;

    @Value("${tally.ledger-applicable-from:20260401}")
    private String applicableFrom;

    private static final String CUSTOMER_GROUP = "Sundry Debtors";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper;

    public TallyLedgerService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public JsonNode createCustomerLedger(MasterCustomer customer) throws Exception {
        validateCustomer(customer);

        JsonNode createResult = syncCustomerLedger(customer, "Create");

        if (isSuccessfulImport(createResult)) {
            return createResult;
        }

        JsonNode alterResult = syncCustomerLedger(customer, "Alter");

        if (isSuccessfulImport(alterResult)) {
            return alterResult;
        }

        throw new IllegalStateException(
                "Customer ledger creation and update both failed. Create response: "
                        + createResult.toPrettyString()
                        + " Alter response: "
                        + alterResult.toPrettyString()
        );
    }

    public JsonNode alterCustomerLedger(MasterCustomer customer) throws Exception {
        validateCustomer(customer);

        JsonNode result = syncCustomerLedger(customer, "Alter");

        if (!isSuccessfulImport(result)) {
            throw new IllegalStateException(
                    "Customer ledger update failed: " + result.toPrettyString()
            );
        }

        return result;
    }

    private JsonNode syncCustomerLedger(MasterCustomer customer, String action) throws Exception {
        ObjectNode root = mapper.createObjectNode();

        ArrayNode variables = root.putArray("static_variables");
        addVariable(variables, "svMstImportFormat", "jsonex");
        addVariable(variables, "svCurrentCompany", companyName);

        ArrayNode messages = root.putArray("tallymessage");
        ObjectNode ledger = messages.addObject();

        ObjectNode metadata = ledger.putObject("metadata");
        metadata.put("type", "Ledger");
        metadata.put("action", action);
        metadata.put("name", customer.getCompanyName().trim());

        ledger.put("name", customer.getCompanyName().trim());
        ledger.put("parent", CUSTOMER_GROUP);

        putIfPresent(ledger, "email", customer.getEmail());
        putIfPresent(ledger, "ledgerphone", customer.getPhone());
        putIfPresent(ledger, "ledgermobile", customer.getMobile());
        putIfPresent(ledger, "ledgercountryisdcode", getCountryCode(customer));
        putIfPresent(ledger, "incometaxnumber", customer.getPan());
        putIfPresent(ledger, "partygstin", customer.getGstin());
        putIfPresent(ledger, "gstregistrationtype", customer.getGstRegistrationType());
        putIfPresent(ledger, "priorstatename", customer.getState());
        putIfPresent(ledger, "oldledstatename", customer.getState());
        putIfPresent(ledger, "countryofresidence", customer.getCountry());

        ledger.put("isbillwiseon", Boolean.TRUE.equals(customer.getMaintainBillByBill()));

        addMailingDetails(ledger, customer);
        addGstDetails(ledger, customer);
        addPaymentDetails(ledger, customer);
        addContactDetails(ledger, customer);

        return sendToTally(root);
    }

    private JsonNode sendToTally(ObjectNode root) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("version", "1");
        headers.set("tallyrequest", "Import");
        headers.set("type", "Data");
        headers.set("id", "All Masters");

        HttpEntity<String> request = new HttpEntity<>(
                mapper.writeValueAsString(root), headers
        );

        ResponseEntity<String> response = restTemplate.exchange(
                tallyBaseUrl, HttpMethod.POST, request, String.class
        );

        String body = response.getBody();

        if (body == null || body.trim().isEmpty()) {
            throw new IllegalStateException("Tally returned an empty response");
        }

        return mapper.readTree(body);
    }

    private boolean isSuccessfulImport(JsonNode result) {
        if (!"1".equals(result.path("status").asText())) {
            return false;
        }

        JsonNode importResult = result.path("data").path("import_result");

        if (importResult.isMissingNode() || importResult.isNull()) {
            return false;
        }

        return importResult.path("errors").asInt() == 0
                && importResult.path("exceptions").asInt() == 0
                && importResult.path("created").asInt()
                + importResult.path("altered").asInt() > 0;
    }

    private void addMailingDetails(ObjectNode ledger, MasterCustomer customer) {
        boolean hasData = !isBlank(customer.getDisplayName())
                || !isBlank(customer.getAddressLine1())
                || !isBlank(customer.getAddressLine2())
                || !isBlank(customer.getCity())
                || !isBlank(customer.getPincode())
                || !isBlank(customer.getState())
                || !isBlank(customer.getCountry());

        if (!hasData) {
            return;
        }

        ArrayNode details = ledger.putArray("ledmailingdetails");
        ObjectNode mailing = details.addObject();

        mailing.put("applicablefrom", applicableFrom);
        putIfPresent(mailing, "mailingname", firstNonBlank(customer.getDisplayName(), customer.getCompanyName()));

        ArrayNode address = mailing.putArray("address");
        addIfPresent(address, customer.getAddressLine1());
        addIfPresent(address, customer.getAddressLine2());
        addIfPresent(address, customer.getCity());

        putIfPresent(mailing, "pincode", customer.getPincode());
        putIfPresent(mailing, "state", customer.getState());
        putIfPresent(mailing, "country", customer.getCountry());
    }

    private void addGstDetails(ObjectNode ledger, MasterCustomer customer) {
        if (isBlank(customer.getGstin()) && isBlank(customer.getGstRegistrationType())) {
            return;
        }

        ArrayNode details = ledger.putArray("ledgstregdetails");
        ObjectNode gst = details.addObject();

        gst.put("applicablefrom", applicableFrom);
        putIfPresent(gst, "gstregistrationtype", customer.getGstRegistrationType());
        putIfPresent(gst, "placeofsupply", customer.getState());
        putIfPresent(gst, "gstin", customer.getGstin());

        gst.put("isothterritoryassessee", false);
        gst.put("considerpurchaseforexport", false);
        gst.put("istransporter", false);
        gst.put("iscommonparty", false);
    }

    private void addPaymentDetails(ObjectNode ledger, MasterCustomer customer) {
        boolean hasData = !isBlank(customer.getBankName())
                || !isBlank(customer.getBankAccountNumber())
                || !isBlank(customer.getBankIfsc())
                || !isBlank(customer.getBankAccountHolderName());

        if (!hasData) {
            return;
        }

        ArrayNode details = ledger.putArray("paymentdetails");
        ObjectNode payment = details.addObject();

        putIfPresent(payment, "bankname", customer.getBankName());
        putIfPresent(payment, "accountnumber", customer.getBankAccountNumber());
        putIfPresent(payment, "ifscode", customer.getBankIfsc());
        putIfPresent(payment, "paymentfavouring", firstNonBlank(customer.getBankAccountHolderName(), customer.getCompanyName()));

        payment.put("transactionname", "Primary");
        payment.put("setasdefault", false);
        payment.put("defaulttransactiontype", "UPI");
    }

    private void addContactDetails(ObjectNode ledger, MasterCustomer customer) {
        String phone = firstNonBlank(customer.getMobile(), customer.getPhone());

        if (isBlank(phone)) {
            return;
        }

        ArrayNode details = ledger.putArray("contactdetails");
        ObjectNode contact = details.addObject();

        contact.put("name", "Primary Mobile No.");
        contact.put("phonenumber", phone);
        contact.put("countryisdcode", getCountryCode(customer));
        contact.put("isdefaultwhatsappnum", true);
    }

    private void validateCustomer(MasterCustomer customer) {
        if (customer == null) {
            throw new IllegalArgumentException("Customer cannot be null");
        }

        if (isBlank(customer.getCompanyName())) {
            throw new IllegalArgumentException("Customer company name is required: " + customer.getId());
        }
    }

    private void addVariable(ArrayNode variables, String name, String value) {
        ObjectNode variable = variables.addObject();
        variable.put("name", name);
        variable.put("value", value);
    }

    private String getCountryCode(MasterCustomer customer) {
        return "+91";
    }

    private void putIfPresent(ObjectNode node, String field, String value) {
        if (!isBlank(value)) {
            node.put(field, value.trim());
        }
    }

    private void addIfPresent(ArrayNode array, String value) {
        if (!isBlank(value)) {
            array.add(value.trim());
        }
    }

    private String firstNonBlank(String first, String second) {
        if (!isBlank(first)) {
            return first.trim();
        }

        if (!isBlank(second)) {
            return second.trim();
        }

        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
