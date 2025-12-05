package com.inovexx.order_service.mapper;


import com.inovexx.order_service.dto.OrderDto;
import com.inovexx.order_service.entity.Order;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrderMapper {


    OrderDto orderToOrderDto(Order order);

    Order orderDtoToOrder(OrderDto orderDto);

    void updateOrderFromDto(Order orderDto, @MappingTarget Order order);

}
