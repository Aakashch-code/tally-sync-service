package com.example.tally.masterstock.repository;

import com.example.tally.masterstock.entity.MasterVendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterVendorRepository extends JpaRepository<MasterVendor, Long> {
}