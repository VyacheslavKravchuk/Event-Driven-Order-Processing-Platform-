package com.inovexx.product_service.mapper;

import com.inovexx.product_service.dto.ProductDto;
import com.inovexx.product_service.dto.ProductResponse;
import com.inovexx.product_service.model.Product;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProductMapper {

    ProductResponse productToProductDto(Product product);

    Product productDtoToProduct(ProductDto productDto);

    void updateProductFromDto(Product productDto, @MappingTarget Product user);
}
