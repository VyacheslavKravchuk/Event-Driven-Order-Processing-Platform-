package com.inovexx.inventory_service.repository;


import com.inovexx.inventory_service.entity.ReservationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReservationLogRepository  extends JpaRepository<ReservationLog, String> {

    boolean existsByOrderIdAndProductIdAndStatus(String orderId, String productId, String status);

    boolean existsByOrderIdAndStatus(String orderId, String status);

    List<ReservationLog> findByOrderIdAndStatus(String orderId, String status);
}
