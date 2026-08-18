package com.inovexx.order_service.repository;

import jakarta.transaction.Transactional;
import com.inovexx.order_service.entity.Order;
import com.inovexx.order_service.enums.OrderStatus;
import com.inovexx.order_service.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * Стандартный поиск по UUID.
     * Spring Data JPA понимает UUID автоматически.
     */
    Optional<Order> findById(UUID id);

    /**
     * Поиск всех заказов конкретного пользователя.
     * Полезно для истории заказов в API.
     */
    List<Order> findByUserId(Long userId);

    /**
     * Поиск заказов по статусу.
     * Нужно для работы Саги (например, найти все заказы в статусе PENDING).
     */
    List<Order> findByStatus(String status);

    /**
     * Проверка существования заказа.
     */
    boolean existsById(UUID id);

    /**
     * Пример использования кастомного запроса (JPQL).
     * Позволяет эффективно обновить статус заказа по его UUID.
     */
    @Query("UPDATE Order o SET o.status = :status WHERE o.id = :id")
    void updateOrderStatus(@Param("id") UUID id, @Param("status") String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           select distinct o
           from Order o
           left join fetch o.orderItems oi
           where o.orderId = :orderId
           """)
    Optional<Order> findByIdForUpdate(@Param("orderId") UUID orderId);


    @Modifying
    @Transactional
    @Query("UPDATE Order o SET o.status = :status WHERE o.orderId = :orderId")
    void updateStatus(@Param("orderId") UUID orderId, @Param("status") OrderStatus status);

    @Modifying
    @Transactional
    @Query("UPDATE Order o SET o.paymentStatus = :paymentStatus WHERE o.orderId = :orderId")
    void updatePaymentStatus(@Param("orderId") UUID orderId, @Param("paymentStatus") PaymentStatus paymentStatus);

//    @Modifying
//    @Transactional
//    @Query("UPDATE OrderItem i SET i.reservationReleased = true, i.reservationReleasedAt = :now WHERE i.orderId = :orderId")
//    void markAllOrderItemsReservationReleased(
//            @Param("orderId") UUID orderId,
//            @Param("now") OffsetDateTime now
//    );

//    @Lock(LockModeType.PESSIMISTIC_WRITE)
//    @Query("select o from Order o where o.orderId = :orderId")
//    Optional<Order> findByIdForUpdate(@Param("orderId") UUID orderId);

    @Query("""
    select distinct o
    from Order o
    left join fetch o.orderItems
    where o.orderId = :orderId
""")
    Optional<Order> findByIdWithItems(@Param("orderId") UUID orderId);


}