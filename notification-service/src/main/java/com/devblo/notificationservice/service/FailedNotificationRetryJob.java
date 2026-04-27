package com.devblo.notificationservice.service;

import com.devblo.notificationservice.entity.FailedNotification;
import com.devblo.notificationservice.event.ResultCreatedEvent;
import com.devblo.notificationservice.repositories.IFailedNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class FailedNotificationRetryJob {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 10;
    private static final long BASE_DELAY_SECONDS = 60;
    private static final long MAX_DELAY_SECONDS = 7200;

    private final IFailedNotificationRepository repository;
    private final NotificationManager notificationManager;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${notification.retry.fixed-delay-ms:60000}")
    @Transactional
    public void retry() {
        var due = repository.findDueForRetry(Instant.now(), PageRequest.of(0, BATCH_SIZE));
        if (due.isEmpty()) return;

        log.info("Retrying {} failed notification(s)", due.size());

        for (FailedNotification row : due) {
            ResultCreatedEvent event = deserialize(row);
            if (event == null) {
                markDeadLetter(row, "payload deserialization failed");
                continue;
            }

            try {
                notificationManager.sendFraudAlert(event);
                row.setStatus("SENT");
                repository.save(row);
                log.info("Retry SENT: tx#{} after {} attempt(s)",
                        row.getTransactionId(), row.getAttempts() + 1);
            } catch (Exception ex) {
                int nextAttempt = row.getAttempts() + 1;
                row.setAttempts(nextAttempt);
                row.setLastError(ex.getClass().getSimpleName() + ": " + ex.getMessage());

                if (nextAttempt >= MAX_ATTEMPTS) {
                    row.setStatus("DEAD_LETTER");
                    log.error("Notification DEAD_LETTER after {} attempts: tx#{}",
                            nextAttempt, row.getTransactionId());
                } else {
                    long delay = Math.min(BASE_DELAY_SECONDS << (nextAttempt - 1), MAX_DELAY_SECONDS);
                    row.setNextAttemptAt(Instant.now().plus(delay, ChronoUnit.SECONDS));
                    log.warn("Retry failed: tx#{} attempts={} nextIn={}s",
                            row.getTransactionId(), nextAttempt, delay);
                }
                repository.save(row);
            }
        }
    }

    private ResultCreatedEvent deserialize(FailedNotification row) {
        try {
            return objectMapper.readValue(row.getPayload(), ResultCreatedEvent.class);
        } catch (Exception ex) {
            log.error("Cannot deserialize failed_notification id={}: {}", row.getId(), ex.getMessage());
            return null;
        }
    }

    private void markDeadLetter(FailedNotification row, String reason) {
        row.setStatus("DEAD_LETTER");
        row.setLastError(reason);
        repository.save(row);
    }
}
