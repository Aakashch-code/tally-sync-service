package com.example.tally.outboundsync.masterstock.repository;

import com.example.tally.outboundsync.masterstock.entity.MasterStockItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MasterStockItemRepository extends JpaRepository<MasterStockItem, Long> {
    Optional<MasterStockItem> findByItemNameIgnoreCase(String itemName);
}