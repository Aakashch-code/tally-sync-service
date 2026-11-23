package com.example.tally.outboundsync.purchasemodule.repository;

import com.example.tally.outboundsync.purchasemodule.entity.MasterPurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MasterPurchaseOrderRepository extends JpaRepository<MasterPurchaseOrder, Long> {
    List<MasterPurchaseOrder> findByTallySyncStatus(String status);
    Optional<MasterPurchaseOrder> findByPoNumber(String poNumber);
}