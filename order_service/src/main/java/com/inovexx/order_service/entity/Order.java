package com.inovexx.order_service.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.inovexx.order_service.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

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

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems;

    @Version
    @JsonIgnore
    @Column(name = "version")
    private Long version;

}
