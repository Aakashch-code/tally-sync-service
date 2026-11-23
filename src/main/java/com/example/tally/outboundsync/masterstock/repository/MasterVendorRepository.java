package com.example.tally.outboundsync.masterstock.repository;

import com.example.tally.outboundsync.masterstock.entity.MasterVendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MasterVendorRepository extends JpaRepository<MasterVendor, Long> {
    Optional<MasterVendor> findByCompanyNameIgnoreCase(String companyName);
}