package com.example.tally.purchasemodule.repository;

import com.example.tally.purchasemodule.entity.MasterPurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MasterPurchaseOrderRepository extends JpaRepository<MasterPurchaseOrder, Long> {
    List<MasterPurchaseOrder> findByTallySyncStatus(String status);
}