package com.example.tally.outboundsync.invoice.infrastructure.persistence;

import com.example.tally.outboundsync.invoice.model.MasterCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterCustomerRepository extends JpaRepository<MasterCustomer, Long> {
}