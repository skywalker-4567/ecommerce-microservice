package com.ecommerce.notification_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentResultEvent {
    private Long orderId;
    private Long userId;
    private boolean success;
    private String message;
}
