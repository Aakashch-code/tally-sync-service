package com.example.tally.masterstock.entity;

import lombok.Data;
import javax.persistence.*;

@Data
@Entity
@Table(name = "master_vendors")
public class MasterVendor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String companyName;
    private String state;
    private String country;
    private String gstin;
    private String gstRegistrationType;

    private String tallySyncStatus = "PENDING";
}