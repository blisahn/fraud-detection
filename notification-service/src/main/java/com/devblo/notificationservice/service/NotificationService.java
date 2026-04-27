package com.devblo.notificationservice.service;

import com.devblo.notificationservice.event.ResultCreatedEvent;

public interface NotificationService {
    void sendFraudAlert(ResultCreatedEvent event);
    String getType();
}