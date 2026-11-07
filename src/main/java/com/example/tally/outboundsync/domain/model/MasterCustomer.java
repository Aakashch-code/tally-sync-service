package com.example.tally.outboundsync.domain.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "master_customers")
public class MasterCustomer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String companyName;

    private String displayName;

    private String email;

    private Integer groupCompanyId;

    private Boolean isActive = true;

    private String phone;

    private String mobile;

    private String addressLine1;

    private String addressLine2;

    private String city;

    private String state;

    private String country;

    private String pincode;

    private String pan;

    private String gstin;

    private String gstRegistrationType;

    private Boolean maintainBillByBill = true;

    private String bankName;

    private String bankAccountHolderName;

    private String bankAccountNumber;

    private String bankIfsc;

    private String bankBranch;
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}