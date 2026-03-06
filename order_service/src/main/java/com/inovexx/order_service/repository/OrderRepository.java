package com.inovexx.order_service.repository;

import com.inovexx.order_service.entity.Order;
import com.inovexx.order_service.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Modifying
    @Query("UPDATE Order o SET o.status = :status WHERE o.orderId = :id")
    void updateStatus(@Param("id") Long id, @Param("status") OrderStatus status);

}