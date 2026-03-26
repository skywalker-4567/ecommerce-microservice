package com.ecommerce.payment_service.service;

import com.ecommerce.payment_service.config.RabbitMQConfig;
import com.ecommerce.payment_service.dto.PaymentRequestEvent;
import com.ecommerce.payment_service.dto.PaymentResultEvent;
import com.ecommerce.payment_service.entity.Payment;
import com.ecommerce.payment_service.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final AmqpTemplate amqpTemplate;

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_REQUEST_QUEUE)
    public void processPayment(PaymentRequestEvent event) {
        log.info("Processing payment for order: {}", event.getOrderId());

        // Simulate payment — 80% success rate
        boolean success = Math.random() > 0.2;

        Payment payment = Payment.builder()
                .orderId(event.getOrderId())
                .userId(event.getUserId())
                .amount(event.getAmount())
                .status(success ? Payment.PaymentStatus.SUCCESS : Payment.PaymentStatus.FAILED)
                .processedAt(LocalDateTime.now())
                .build();

        paymentRepository.save(payment);

        PaymentResultEvent result = new PaymentResultEvent(
                event.getOrderId(),
                event.getUserId(),
                success,
                success ? "Payment successful" : "Payment failed"
        );

        // Publish result back to order-service
        amqpTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.PAYMENT_RESULT_QUEUE,
                result
        );

        // Publish to notification queue
        amqpTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.NOTIFICATION_QUEUE,
                result
        );

        log.info("Payment {} for order {}", success ? "succeeded" : "failed", event.getOrderId());
    }
}
