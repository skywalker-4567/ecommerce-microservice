package com.ecommerce.notification_service.service;

import com.ecommerce.notification_service.config.RabbitMQConfig;
import com.ecommerce.notification_service.dto.PaymentResultEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationService {

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
    public void handleNotification(PaymentResultEvent event) {
        if (event.isSuccess()) {
            log.info("EMAIL → User {}: Your order {} has been confirmed. Payment successful.",
                    event.getUserId(), event.getOrderId());
        } else {
            log.info("EMAIL → User {}: Your order {} was cancelled. Payment failed.",
                    event.getUserId(), event.getOrderId());
        }
    }
}
