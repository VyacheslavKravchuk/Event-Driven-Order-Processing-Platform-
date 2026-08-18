package com.inovexx.inventory_service.entity;


import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReservationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String orderId;
    private String productId;
    private String status; // "RESERVED" или "CANCELLED"
    private int quantity;
    private OffsetDateTime timestamp;

}
