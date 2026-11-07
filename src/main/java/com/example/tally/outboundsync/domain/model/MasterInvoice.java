package com.example.tally.outboundsync.domain.model;

import lombok.Data;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "master_invoices")
public class MasterInvoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private BigDecimal totalAmount;
    private Long customerId;
    private String tallySyncStatus = "PENDING";
    private Integer groupCompanyId;
}