package com.example.tally.outboundsync.infrastructure.persistence;

import com.example.tally.outboundsync.domain.model.MasterInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MasterInvoiceRepository extends JpaRepository<MasterInvoice, Long> {

    List<MasterInvoice> findByTallySyncStatus(String status);
}