package com.example.tally.mastermodule.repository;

import com.example.tally.mastermodule.entity.MasterStockItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterStockItemRepository extends JpaRepository<MasterStockItem, Long> {
}