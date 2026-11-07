package com.example.tally.mastermodule.repository;

import com.example.tally.mastermodule.entity.MasterVendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterVendorRepository extends JpaRepository<MasterVendor, Long> {
}