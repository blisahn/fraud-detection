package com.devblo.notificationservice.event;

import java.time.Instant;

public record NotificationCompletedEvent(Long transactionId,
                                         String notificationType,
                                         String status,
                                         Instant completedAt) {
}
