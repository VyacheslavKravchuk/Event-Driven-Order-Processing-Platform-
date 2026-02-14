package com.inovexx.inventory_service.repository;

import com.inovexx.inventory_service.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductId(String productId);

    boolean existsByProductId(String productId);

    void deleteByProductId(String productId);
}