package com.devblo.notificationservice.service;

import com.devblo.notificationservice.entity.FailedNotification;
import com.devblo.notificationservice.event.ResultCreatedEvent;
import com.devblo.notificationservice.repositories.IFailedNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class FailedNotificationService {

    private final IFailedNotificationRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void queueForRetry(ResultCreatedEvent event, String notificationType, Throwable cause) {
        FailedNotification row = new FailedNotification();
        row.setTransactionId(event.transactionId());
        row.setPayload(toJson(event));
        row.setNotificationType(notificationType);
        row.setLastError(cause.getClass().getSimpleName() + ": " + cause.getMessage());
        row.setAttempts(0);
        row.setNextAttemptAt(Instant.now().plus(1, ChronoUnit.MINUTES));
        row.setStatus("PENDING");
        repository.save(row);
        log.warn("Notification queued for retry: tx#{} type={} cause={}",
                event.transactionId(), notificationType, row.getLastError());
    }

    private String toJson(ResultCreatedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize ResultCreatedEvent", ex);
        }
    }
}
