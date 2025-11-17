package com.inovexx.product_service.service.impl;

import java.util.List;
import com.inovexx.product_service.dto.ProductRequest;
import com.inovexx.product_service.dto.ProductResponse;
import com.inovexx.product_service.model.Product;
import com.inovexx.product_service.repository.ProductRepository;
import com.inovexx.product_service.service.ProductService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public void createProduct(ProductRequest productRequest) {
        log.info("Начало создания продукта: {}", productRequest);
        Product product = Product.builder()
                .name(productRequest.getName())
                .description(productRequest.getDescription())
                .price(productRequest.getPrice())
                .build();

        productRepository.save(product);
        log.info("Продукт успешно сохранен с ID: {}", product.getId());

        kafkaTemplate.send("productTopic", "Product created: " + product.getName());
        log.info("Сообщение отправлено в Kafka для продукта: {}", product.getName());
    }

    public List<ProductResponse> getAllProducts() {
        log.info("Получен запрос на получение всех продуктов.");
        List<Product> products = productRepository.findAll();
        List<ProductResponse> productResponses = products.stream()
                .map(this::mapToProductResponse).collect(Collectors.toList());
        log.info("Возвращено {} продуктов.", productResponses.size());
        return productResponses;
    }

    @Override
    public ProductResponse mapToProductResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .build();
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(String id, ProductRequest productRequest) {
        log.info("Начало обновления продукта с ID: {}, данные: {}", id, productRequest);
        Optional<Product> optionalProduct = productRepository.findById(id);

        if (optionalProduct.isPresent()) {
            Product product = optionalProduct.get();
            product.setName(productRequest.getName());
            product.setDescription(productRequest.getDescription());
            product.setPrice(productRequest.getPrice());

            Product updatedProduct = productRepository.save(product);
            log.info("Продукт с ID: {} успешно обновлен.", updatedProduct.getId());
            kafkaTemplate.send("productTopic", "Product updated: " + updatedProduct.getName());
            log.info("Сообщение отправлено в Kafka об обновлении продукта: {}", updatedProduct.getName());
            return mapToProductResponse(updatedProduct);

        } else {
            log.warn("Продукт с ID: {} не найден.", id);
            throw new ProductNotFoundException("Product not found with id: " + id);
        }
    }

    @Override
    public void deleteProduct(String id) {
        log.info("Начало удаления продукта с ID: {}", id);
        if (productRepository.existsById(id)) {
            productRepository.deleteById(id);
            log.info("Продукт с ID: {} успешно удален.", id);
            kafkaTemplate.send("productTopic", "Product deleted: " + id);
            log.info("Сообщение отправлено в Kafka об удалении продукта с ID: {}", id);
        } else {
            log.warn("Продукт с ID: {} не найден и не может быть удален.", id);
            throw new ProductNotFoundException("Product not found with id: " + id);
        }
    }
}
