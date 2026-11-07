package com.example.tally.outboundsync.infrastructure.persistence;

import com.example.tally.outboundsync.domain.model.MasterCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterCustomerRepository extends JpaRepository<MasterCustomer, Long> {
}