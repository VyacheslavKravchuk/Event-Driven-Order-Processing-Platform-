package com.inovexx.product_service.service;

import com.inovexx.product_service.dto.ProductDto;

import java.util.List;

public interface ProductService {

    String  createProduct(ProductDto productDto);

    List<ProductDto> getAllProducts();

    ProductDto updateProduct(String id, ProductDto productDto);

    void deleteProduct(String id);
}
