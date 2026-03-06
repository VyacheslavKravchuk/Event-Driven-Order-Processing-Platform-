package com.inovexx.order_service.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.inovexx.order_service.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @Column(nullable = false)
    private Long userId; // Идентификатор клиента

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private OffsetDateTime orderDate;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

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

}