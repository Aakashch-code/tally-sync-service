package com.example.tally.inboundsync.service;

import com.example.tally.outboundsync.domain.model.MasterCustomer;
import com.example.tally.outboundsync.domain.model.MasterInvoice;
import com.example.tally.outboundsync.infrastructure.persistence.MasterCustomerRepository;
import com.example.tally.outboundsync.infrastructure.persistence.MasterInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class TallyImportService {

    private final MasterCustomerRepository customerRepository;
    private final MasterInvoiceRepository invoiceRepository;
    private final RestTemplate restTemplate;

    @Value("${tally.base-url:http://localhost:9000}")
    private String tallyBaseUrl;

    public void importCustomersAndInvoices() throws Exception {
        String ledgerXmlResponse = fetchFromTally("List of Accounts", "Ledgers");
        processLedgers(ledgerXmlResponse);

        String voucherXmlResponse = fetchFromTally("Day Book", "Vouchers");
        processVouchers(voucherXmlResponse);
    }

    private String fetchFromTally(String reportName, String accountType) {
        String requestXml = "<ENVELOPE>\n" +
                "  <HEADER><TALLYREQUEST>Export Data</TALLYREQUEST></HEADER>\n" +
                "  <BODY>\n" +
                "    <EXPORTDATA>\n" +
                "      <REQUESTDESC>\n" +
                "        <REPORTNAME>" + reportName + "</REPORTNAME>\n" +
                "        <STATICVARIABLES>\n" +
                "          <SVEXPORTFORMAT>$$SysName:XML</SVEXPORTFORMAT>\n" +
                "          <ACCOUNTTYPE>" + accountType + "</ACCOUNTTYPE>\n" +
                "        </STATICVARIABLES>\n" +
                "      </REQUESTDESC>\n" +
                "    </EXPORTDATA>\n" +
                "  </BODY>\n" +
                "</ENVELOPE>";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_XML);
        HttpEntity<String> request = new HttpEntity<>(requestXml, headers);

        return restTemplate.postForObject(tallyBaseUrl, request, String.class);
    }

    private void processLedgers(String xmlData) throws Exception {
        Document doc = parseXml(xmlData);
        NodeList ledgers = doc.getElementsByTagName("LEDGER");

        for (int i = 0; i < ledgers.getLength(); i++) {
            Element ledger = (Element) ledgers.item(i);
            String parent = getTagValue(ledger, "PARENT");

            if ("Sundry Debtors".equalsIgnoreCase(parent)) {
                String companyName = ledger.getAttribute("NAME");

                MasterCustomer customer = customerRepository.findAll().stream()
                        .filter(c -> c.getCompanyName().equalsIgnoreCase(companyName))
                        .findFirst()
                        .orElse(new MasterCustomer());

                if (customer.getId() == null) {
                    customer.setId(System.currentTimeMillis());
                }

                customer.setCompanyName(companyName);
                customer.setDisplayName(companyName);
                customer.setEmail(getTagValue(ledger, "EMAIL"));

                String state = getTagValue(ledger, "LEDSTATENAME");
                if (state == null || state.trim().isEmpty()) state = getTagValue(ledger, "STATENAME");
                if (state == null || state.trim().isEmpty()) state = getTagValue(ledger, "PRIORSTATENAME");
                customer.setState(state);

                customer.setCountry(getFallbackTagValue(ledger, "COUNTRYOFRESIDENCE", "COUNTRYNAME"));
                customer.setPincode(getTagValue(ledger, "PINCODE"));

                customer.setPan(getFallbackTagValue(ledger, "INCOMETAXNUMBER", "PANNUMBER"));
                customer.setGstRegistrationType(getMultiFallbackTagValue(ledger,
                        "GSTREGISTRATIONTYPE", "PARTYGSTREGTYPE", "REGISTRATIONTYPE"));

                customer.setGstin(getMultiFallbackTagValue(ledger, "PARTYGSTIN", "GSTIN"));

                customer.setBankAccountNumber(getTagValue(ledger, "ACCOUNTNUMBER"));
                customer.setBankName(getTagValue(ledger, "BANKNAME"));
                customer.setBankAccountHolderName(getMultiFallbackTagValue(ledger, "PAYMENTFAVOURING", "BENEFICIARYNAME"));

                customer.setBankBranch(getMultiFallbackTagValue(ledger,
                        "BANKBRANCH", "BRANCHNAME", "BRANCH"));

                customer.setBankIfsc(getMultiFallbackTagValue(ledger,
                        "IFSCODE", "IFSC", "BANKIFSC"));

                NodeList addressNodes = ledger.getElementsByTagName("ADDRESS");
                if (addressNodes.getLength() > 0) {
                    customer.setAddressLine1(addressNodes.item(0).getTextContent());
                }
                if (addressNodes.getLength() > 1) {
                    customer.setAddressLine2(addressNodes.item(1).getTextContent());
                }
                customer.setIsActive(true);
                customerRepository.save(customer);
            }
        }
    }

    private void processVouchers(String xmlData) throws Exception {
        Document doc = parseXml(xmlData);
        NodeList vouchers = doc.getElementsByTagName("VOUCHER");

        for (int i = 0; i < vouchers.getLength(); i++) {
            Element voucher = (Element) vouchers.item(i);
            String voucherType = voucher.getAttribute("VCHTYPE");

            if ("Sales".equalsIgnoreCase(voucherType)) {
                String dateStr = getTagValue(voucher, "DATE");
                String partyName = getTagValue(voucher, "PARTYLEDGERNAME");

                MasterCustomer customer = customerRepository.findAll().stream()
                        .filter(c -> c.getCompanyName().equalsIgnoreCase(partyName))
                        .findFirst()
                        .orElse(null);

                if (customer == null) {
                    System.out.println("Skipping voucher: Customer not found in DB -> " + partyName);
                    continue;
                }

                NodeList ledgerEntries = voucher.getElementsByTagName("ALLLEDGERENTRIES.LIST");
                BigDecimal totalAmount = BigDecimal.ZERO;

                String invoiceNumber = getTagValue(voucher, "VOUCHERNUMBER");

                for (int j = 0; j < ledgerEntries.getLength(); j++) {
                    Element entry = (Element) ledgerEntries.item(j);
                    String ledgerName = getTagValue(entry, "LEDGERNAME");

                    if (partyName.equalsIgnoreCase(ledgerName)) {
                        String amountStr = getTagValue(entry, "AMOUNT");
                        if (amountStr != null) {
                            totalAmount = new BigDecimal(amountStr).abs();
                        }

                        NodeList billAllocations = entry.getElementsByTagName("BILLALLOCATIONS.LIST");
                        if (billAllocations != null && billAllocations.getLength() > 0) {
                            Element bill = (Element) billAllocations.item(0);
                            String billName = getTagValue(bill, "NAME");
                            if (billName != null && !billName.isEmpty()) {
                                invoiceNumber = billName;
                            }
                        }
                    }
                }

                if (invoiceNumber == null || invoiceNumber.trim().isEmpty()) {
                    continue;
                }

                String finalInvoiceNumber = invoiceNumber;
                MasterInvoice invoice = invoiceRepository.findAll().stream()
                        .filter(inv -> inv.getInvoiceNumber().equals(finalInvoiceNumber))
                        .findFirst()
                        .orElse(new MasterInvoice());


                invoice.setInvoiceNumber(finalInvoiceNumber);
                invoice.setCustomerId(customer.getId());
                invoice.setTallySyncStatus("SYNCED");
                invoice.setGroupCompanyId(1);

                if (dateStr != null && !dateStr.isEmpty()) {
                    invoice.setInvoiceDate(LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd")));
                }

                invoice.setTotalAmount(totalAmount);

                invoiceRepository.save(invoice);
                System.out.println("Synced Invoice: " + finalInvoiceNumber + " for " + partyName);
            }
        }
    }

    private String getMultiFallbackTagValue(Element element, String... tags) {
        for (String tag : tags) {
            String value = getTagValue(element, tag);
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }
    private Document parseXml(String xmlData) throws Exception {
        String sanitizedXml = sanitizeXml(xmlData);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(sanitizedXml)));
    }

    private String getTagValue(Element element, String tagName) {
        NodeList nodeList = element.getElementsByTagName(tagName);
        if (nodeList != null && nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return null;
    }
    private String sanitizeXml(String xml) {
        if (xml == null) {
            return null;
        }

        String cleaned = xml.replaceAll("[^\\x09\\x0A\\x0D\\x20-\\uD7FF\\uE000-\\uFFFD\\u10000-\\u10FFFF]", "");
        cleaned = cleaned.replaceAll("&#(?:x?[0-8B-Ce-fA-F]|1[1-24-9]|2[0-9]|3[0-1]);", "");

        return cleaned;
    }
    private String getFallbackTagValue(Element element, String primaryTag, String fallbackTag) {
        String value = getTagValue(element, primaryTag);
        if (value != null && !value.trim().isEmpty()) {
            return value;
        }
        return getTagValue(element, fallbackTag);
    }
}