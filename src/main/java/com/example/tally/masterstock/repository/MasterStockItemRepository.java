package com.example.tally.masterstock.repository;

import com.example.tally.masterstock.entity.MasterStockItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterStockItemRepository extends JpaRepository<MasterStockItem, Long> {
}