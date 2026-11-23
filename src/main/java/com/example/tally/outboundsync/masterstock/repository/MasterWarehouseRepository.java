package com.example.tally.outboundsync.masterstock.repository;

import com.example.tally.outboundsync.masterstock.entity.MasterWarehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MasterWarehouseRepository extends JpaRepository<MasterWarehouse, Long> {
    List<MasterWarehouse> findByTallySyncStatus(String status);
    Optional<MasterWarehouse> findByWarehouseNameIgnoreCase(String warehouseName);
}