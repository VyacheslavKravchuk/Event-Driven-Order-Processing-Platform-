package com.inovexx.order_service.entity;

import com.inovexx.order_service.enums.CancellationReason;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.time.OffsetDateTime;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class CancellationDetails {

    @Column(name = "cancel_reason", length = 500)
    private CancellationReason reason;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;
}
