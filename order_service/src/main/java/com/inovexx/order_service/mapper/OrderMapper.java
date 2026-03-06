package com.inovexx.order_service.mapper;

import com.inovexx.order_service.dto.OrderDto;
import com.inovexx.order_service.dto.OrderItemDto;
import com.inovexx.order_service.entity.Order;
import com.inovexx.order_service.entity.OrderItem;
import com.inovexx.order_service.grpc.OrderResponse;
import org.mapstruct.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrderMapper {

    OrderDto orderToOrderDto(Order order);

    Order orderDtoToOrder(OrderDto orderDto);

    OrderItemDto itemToItemDto(OrderItem item);
    OrderItem itemDtoToItem(OrderItemDto dto);

    void updateOrderFromDto(OrderDto dto, @MappingTarget Order order);

    // Сопоставляем поля сущности и gRPC сообщения
    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "status", expression = "java(order.getStatus().name())") // Enum в String
    @Mapping(target = "totalAmount", source = "totalAmount", qualifiedByName = "bigDecimalToDouble")
    // Если в .proto добавите дату, используйте:
    // @Mapping(target = "orderDate", source = "orderDate", qualifiedByName = "offsetDateTimeToString")
    OrderResponse toGrpcResponse(Order order);

    // Вспомогательный метод для конвертации денег
    @Named("bigDecimalToDouble")
    default double bigDecimalToDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : 0.0;
    }

    // Вспомогательный метод для конвертации дат в ISO-8601 строку
    @Named("offsetDateTimeToString")
    default String offsetDateTimeToString(OffsetDateTime dateTime) {
        return dateTime != null ? dateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : "";
    }
}
