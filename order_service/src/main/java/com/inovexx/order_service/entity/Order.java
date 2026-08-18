package com.inovexx.order_service.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.inovexx.order_service.enums.OrderStatus;
import com.inovexx.order_service.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID) // Используем UUID генерацию
    @Column(name = "order_id", columnDefinition = "uuid") // Указываем тип колонки
    private UUID orderId;

    @Column(nullable = false)
    private Long userId;

    @Column(name = "customer_email", nullable = false, length = 320)
    private String customerEmail;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private OffsetDateTime orderDate;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.NOT_CHARGED;

    @Embedded
    private CancellationDetails cancellationDetails; // По умолчанию null

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<OrderItem> orderItems;

    @Version
    @JsonIgnore
    @Column(name = "version")
    private Long version;

    public void addOrderItem(OrderItem item) {
        if (item == null) {
            throw new IllegalArgumentException("OrderItem cannot be null");
        }

        // Инициализируем список, если он вдруг null
        if (this.orderItems == null) {
            this.orderItems = new ArrayList<>();
        }

        // Устанавливаем двустороннюю связь
        this.orderItems.add(item);
        item.setOrder(this);

        // Автоматический пересчет суммы заказа
        recalculateTotal();
    }

    private void recalculateTotal() {
        this.totalAmount = orderItems.stream()
                .filter(item -> item.getPrice() != null)
                .map(item -> item.getPrice().multiply(BigDecimal
                        .valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Бизнес-метод для инициации отмены
//    public void initiateCancellation(String reason) {
//        if (this.status == OrderStatus.CANCELLED) {
//            throw new IllegalStateException("Order is already cancelled");
//        }
//        this.status = OrderStatus.CANCELLATION_REQUESTED; // Если у вас есть такой промежуточный статус
//
//        this.cancellationDetails = new CancellationDetails(
//                reason,
//                OffsetDateTime.now()
//        );
//    }

    // Бизнес-метод для завершения отмены (например, после отката саги)
    public void confirmCancellation() {
        if (this.cancellationDetails == null) {
            throw new IllegalStateException("Cancellation was not initiated");
        }
        this.status = OrderStatus.CANCELLED;
        this.cancellationDetails.setCancelledAt(OffsetDateTime.now());
    }
}
