package com.inovexx.product_service.service;

import com.inovexx.product_service.dto.ProductRequest;
import com.inovexx.product_service.dto.ProductResponse;
import com.inovexx.product_service.model.Product;

import java.util.List;

public interface ProductService {

    void createProduct(ProductRequest productRequest);

    List<ProductResponse> getAllProducts();

    ProductResponse mapToProductResponse(Product product);

    ProductResponse updateProduct(String id, ProductRequest productRequest);

    void deleteProduct(String id);
}
