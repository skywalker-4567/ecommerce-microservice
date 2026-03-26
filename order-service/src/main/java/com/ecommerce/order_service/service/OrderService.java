package com.ecommerce.order_service.service;

import com.ecommerce.order_service.client.ProductClient;
import com.ecommerce.order_service.client.ProductResponse;
import com.ecommerce.order_service.config.RabbitMQConfig;
import com.ecommerce.order_service.dto.*;
import com.ecommerce.order_service.entity.Order;
import com.ecommerce.order_service.entity.OrderItem;
import com.ecommerce.order_service.exception.ResourceNotFoundException;
import com.ecommerce.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final AmqpTemplate amqpTemplate;

    public OrderResponse placeOrder(Long userId, OrderRequest request) {
        Order order = new Order();
        order.setUserId(userId);
        List<OrderItem> items = new ArrayList<>();

        for (OrderItemRequest itemReq : request.getItems()) {
            ProductResponse product = productClient.getProductById(itemReq.getProductId());

            if (product.getStock() < itemReq.getQuantity()) {
                throw new IllegalArgumentException(
                        "Insufficient stock for: " + product.getName());
            }

            items.add(OrderItem.builder()
                    .order(order)
                    .productId(product.getId())
                    .productName(product.getName())
                    .quantity(itemReq.getQuantity())
                    .price(product.getPrice())
                    .build());
        }

        order.setItems(items);
        Order saved = orderRepository.save(order);

        // Reduce stock
        for (OrderItemRequest itemReq : request.getItems()) {
            productClient.reduceStock(itemReq.getProductId(), itemReq.getQuantity());
        }

        // Calculate total
        BigDecimal total = saved.getItems().stream()
                .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Publish payment request — fire and forget
        amqpTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.PAYMENT_REQUEST_QUEUE,
                new PaymentRequestEvent(saved.getId(), userId, total)
        );

        return toResponse(saved);
    }

    // Listen for payment result and update order status
    @RabbitListener(queues = RabbitMQConfig.PAYMENT_RESULT_QUEUE)
    public void handlePaymentResult(PaymentResultEvent event) {
        Order order = orderRepository.findById(event.getOrderId()).orElse(null);
        if (order == null) return;

        order.setStatus(event.isSuccess()
                ? Order.OrderStatus.CONFIRMED
                : Order.OrderStatus.CANCELLED);

        orderRepository.save(order);
    }

    public OrderResponse getOrder(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Access denied");
        }

        return toResponse(order);
    }

    public List<OrderResponse> getUserOrders(Long userId) {
        return orderRepository.findByUserId(userId)
                .stream().map(this::toResponse).toList();
    }

    public OrderResponse updateStatus(Long orderId, Order.OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        order.setStatus(status);
        return toResponse(orderRepository.save(order));
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .subtotal(item.getPrice().multiply(
                                BigDecimal.valueOf(item.getQuantity())))
                        .build())
                .toList();

        BigDecimal total = itemResponses.stream()
                .map(OrderItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .status(order.getStatus().name())
                .items(itemResponses)
                .total(total)
                .createdAt(order.getCreatedAt())
                .build();
    }
}