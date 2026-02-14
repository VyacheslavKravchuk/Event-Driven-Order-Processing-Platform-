package com.inovexx.product_service.service.impl;

import java.time.Instant;
import java.util.List;
import com.inovexx.product_service.dto.ProductDto;
import com.inovexx.product_service.dto.ProductEvent;
import com.inovexx.product_service.exception.ProductNotFoundException;
import com.inovexx.product_service.mapper.ProductMapper;
import com.inovexx.product_service.model.Product;
import com.inovexx.product_service.repository.ProductRepository;
import com.inovexx.product_service.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final KafkaTemplate<String, ProductEvent> kafkaTemplate;

    @Value("${kafka.topic.product-events:product.events}")
    private String productEventsTopic;

    @Override
    @Transactional // Гарантирует атомарность операции в пределах БД
    public String createProduct(ProductDto productDto) {
        log.info("Создание продукта: {}", productDto.name());

        Product product = productMapper.productDtoToProduct(productDto);
        Product savedProduct = productRepository.save(product);

        sendEvent(savedProduct.getId(), "CREATED", savedProduct.getName());

        return savedProduct.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDto> getAllProducts() {
        return productRepository.findAll().stream()
                .map(productMapper::productToProductDto)
                .toList();
    }

    @Override
    @Transactional
    public ProductDto updateProduct(String id, ProductDto productDto) {
        log.info("Обновление продукта ID: {}", id);

        return productRepository.findById(id)
                .map(existingProduct -> {
                    // Обновляем поля через маппер или вручную
                    existingProduct.setName(productDto.name());
                    existingProduct.setDescription(productDto.description());
                    existingProduct.setPrice(productDto.price());
                    existingProduct.setCategory(productDto.category());

                    Product updated = productRepository.save(existingProduct);

                    sendEvent(id, "UPDATED", updated.getName());

                    return productMapper.productToProductDto(updated);
                })
                .orElseThrow(() -> new ProductNotFoundException("Продукт не найден по id: " + id));
    }

    @Override
    @Transactional
    public void deleteProduct(String id) {
        log.info("Удаление продукта ID: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Продукт не найден по id: " + id));

        productRepository.delete(product);

        sendEvent(id, "DELETED", product.getName());
    }

    /**
     * Вспомогательный метод для отправки событий.
     * Можно добавить .whenComplete() для асинхронной обработки результата отправки.
     */
    private void sendEvent(String productId, String type, String name) {
        ProductEvent event = new ProductEvent(productId, type, name, Instant.now());

        kafkaTemplate.send(productEventsTopic, productId, event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Событие {} успешно отправлено в Kafka для ID: {}", type, productId);
                    } else {
                        log.error("Ошибка отправки события в Kafka для ID: {}", productId, ex);
                        // В продакшене тут может быть логика переотправки или отката транзакции
                    }
                });
    }
}

