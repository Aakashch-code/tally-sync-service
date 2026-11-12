package com.example.tally.masterstock.repository;

import com.example.tally.masterstock.entity.MasterWarehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MasterWarehouseRepository extends JpaRepository<MasterWarehouse, Long> {
    List<MasterWarehouse> findByTallySyncStatus(String status);
}