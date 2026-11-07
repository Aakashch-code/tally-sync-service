package com.example.tally.outboundsync.infrastructure.tally.dto;

import lombok.Data;

@Data
public class InvoiceRequest {
    private String date;
    private String voucherNumber;
    private String partyLedgerName;
    private String salesLedgerName;
    private Double amount;
}