package com.example.tally.mastermodule.entity;

import lombok.Data;
import javax.persistence.*;

@Data
@Entity
@Table(name = "master_stock_items")
public class MasterStockItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String itemName;
    private String unit = "Nos";
    private String hsnCode;
    private String taxRate;

    private String tallySyncStatus = "PENDING";
}