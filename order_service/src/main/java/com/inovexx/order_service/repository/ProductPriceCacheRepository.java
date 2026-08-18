package com.inovexx.order_service.repository;

import com.inovexx.order_service.entity.ProductPriceCache;
import com.inovexx.order_service.events.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductPriceCacheRepository
        extends JpaRepository<ProductPriceCache, String> {


}
