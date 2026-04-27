package com.devblo.notificationservice.service;

import com.devblo.notificationservice.event.ResultCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationManager {

    private final Map<String, NotificationService> notificationServices;

    @Value("${notification.enabled-types:EMAIL}")
    private List<String> enabledTypes;

    public void sendFraudAlert(ResultCreatedEvent event) {
        List<NotificationService> activeServices = enabledTypes.stream()
                .map(type -> notificationServices.get(type))
                .filter(service -> service != null)
                .collect(Collectors.toList());

        if (activeServices.isEmpty()) {
            log.warn("No active notification services found for enabled types: {}", enabledTypes);
            return;
        }

        for (NotificationService service : activeServices) {
            try {
                service.sendFraudAlert(event);
            } catch (Exception ex) {
                log.error("Failed to send notification via {} for transaction #{}: {}",
                        service.getType(), event.transactionId(), ex.getMessage());
            }
        }
    }
}