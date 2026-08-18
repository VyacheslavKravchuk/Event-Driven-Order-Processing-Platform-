package com.inovexx.order_service.service.grpc;

import com.inovexx.order_service.entity.Order;
import com.inovexx.order_service.grpc.OrderInternalServiceGrpc;
import com.inovexx.order_service.grpc.OrderRequest;
import com.inovexx.order_service.grpc.OrderResponse;
import com.inovexx.order_service.mapper.OrderMapper;
import com.inovexx.order_service.repository.OrderRepository;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService // Аннотация из grpc-spring-boot-starter
@RequiredArgsConstructor
public class OrderGrpcService extends OrderInternalServiceGrpc.OrderInternalServiceImplBase {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper; // Тот самый интерфейс, для которого создался OrderMapperImpl

//    @Override
//    public void getOrder(OrderRequest request, StreamObserver<OrderResponse> responseObserver) {
//        // 1. Ищем в БД
//        Order order = orderRepository.findById(
//                request.getOrderId()
//                )
//                .orElseThrow(() -> new StatusRuntimeException(Status.NOT_FOUND));
//
//        // 2. Маппим сущность в gRPC ответ
//        OrderResponse response = orderMapper.toGrpcResponse(order);
//
//        // 3. Отправляем клиенту
//        responseObserver.onNext(response);
//        responseObserver.onCompleted();
//    }
}
