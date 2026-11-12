package com.example.tally.masterstock.entity;

import lombok.Data;
import javax.persistence.*;

@Data
@Entity
@Table(name = "master_warehouses")
public class MasterWarehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String warehouseName;
    private String locationAddress;
    private Long vendorId;
    private String tallySyncStatus = "PENDING";
}