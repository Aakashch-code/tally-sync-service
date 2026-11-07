package com.example.tally.purchasemodule.entity;

import lombok.Data;
import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "master_purchase_orders")
public class MasterPurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String poNumber;
    private LocalDate poDate;

    private Long vendorId;
    private Long stockItemId;

    private Integer quantity;
    private BigDecimal ratePerItem;

    private BigDecimal baseAmount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal totalAmount;

    private String tallySyncStatus = "PENDING";
}