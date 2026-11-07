package com.example.tally.outboundsync.service;

import com.example.tally.outboundsync.domain.model.MasterCustomer;
import com.example.tally.outboundsync.domain.model.MasterInvoice;
import com.example.tally.outboundsync.infrastructure.tally.dto.InvoiceRequest;
import com.example.tally.outboundsync.infrastructure.persistence.MasterCustomerRepository;
import com.example.tally.outboundsync.infrastructure.persistence.MasterInvoiceRepository;
import com.example.tally.outboundsync.infrastructure.tally.TallyInvoiceService;
import com.example.tally.outboundsync.infrastructure.tally.TallyLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InvoiceSyncService {

    private final MasterInvoiceRepository invoiceRepository;
    private final MasterCustomerRepository customerRepository;
    private final TallyInvoiceService tallyInvoiceService;
    private final TallyLedgerService tallyLedgerService;

    public String syncPendingInvoices() {

        List<MasterInvoice> pendingInvoices =
                invoiceRepository.findByTallySyncStatus("PENDING");

        int successCount = 0;
        int failedCount = 0;

        for (MasterInvoice invoice : pendingInvoices) {
            try {
                MasterCustomer customer =
                        customerRepository.findById(invoice.getCustomerId())
                                .orElseThrow(() ->
                                        new IllegalStateException(
                                                "Customer not found: "
                                                        + invoice.getCustomerId()
                                        )
                                );

                if (Boolean.FALSE.equals(customer.getIsActive())) {
                    throw new IllegalStateException(
                            "Customer is inactive: " + customer.getId()
                    );
                }

                tallyLedgerService.createCustomerLedger(customer);

                InvoiceRequest request = new InvoiceRequest();
                request.setDate(
                        invoice.getInvoiceDate().format(
                                DateTimeFormatter.ofPattern("yyyyMMdd")
                        )
                );
                request.setVoucherNumber(invoice.getInvoiceNumber());
                request.setAmount(
                        invoice.getTotalAmount().doubleValue()
                );
                request.setPartyLedgerName(customer.getCompanyName());
                request.setSalesLedgerName("Zenso Sales");

                tallyInvoiceService.createSalesInvoice(request);

                invoice.setTallySyncStatus("SYNCED");
                successCount++;

            } catch (Exception e) {
                invoice.setTallySyncStatus("FAILED");
                failedCount++;

                System.err.println(
                        "Invoice " + invoice.getInvoiceNumber()
                                + " failed: " + e.getMessage()
                );
            }

            invoiceRepository.save(invoice);
        }

        return "Sync completed. Total: " + pendingInvoices.size()
                + ", successful: " + successCount
                + ", failed: " + failedCount;
    }
}