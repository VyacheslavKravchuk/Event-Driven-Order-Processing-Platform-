package com.inovexx.order_service.client.kafka;

import com.inovexx.order_service.dto.ProductEvent;
import com.inovexx.order_service.entity.ProductPriceCache;
import com.inovexx.order_service.repository.ProductPriceCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventConsumer {

    private final ProductPriceCacheRepository cacheRepository;

    //@KafkaListener(topics = "${app.kafka.topics.product-events}")
    @KafkaListener(topics = "product.events", groupId = "product-group")
    public void handleProductEvent(ProductEvent event) {
        log.info("Получено событие обновления товара: {}", event.productId());

        if ("PRODUCT_DELETED".equals(event.type())) {
            cacheRepository.deleteById(event.productId());
        } else {
            ProductPriceCache cache = new ProductPriceCache();
            cache.setProductId(event.productId());
            cache.setType(event.type());
            cache.setPrice(event.price()); // Сохраняем свежую цену из Kafka!
            cache.setName(event.name());
            cacheRepository.save(cache);
        }
    }
}
