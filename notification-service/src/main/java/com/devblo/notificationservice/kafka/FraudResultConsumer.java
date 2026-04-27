package com.devblo.notificationservice.kafka;

import com.devblo.notificationservice.entity.ProcessedEvent;
import com.devblo.notificationservice.event.NotificationCompletedEvent;
import com.devblo.notificationservice.event.ResultCreatedEvent;
import com.devblo.notificationservice.repositories.IProcessedEventRepository;
import com.devblo.notificationservice.service.FailedNotificationService;
import com.devblo.notificationservice.service.NotificationManager;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class FraudResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(FraudResultConsumer.class);
    private final IProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;
    private final NotificationManager notificationManager;
    private final NotificationCompletedPublisher notificationCompletedPublisher;
    private final FailedNotificationService failedNotificationService;

    @KafkaListener(topics = "fraud-results", groupId = "notification-service")
    @Transactional
    public void handleFraudResultPublished(ConsumerRecord<String, String> record) {
        String eventKey = record.topic() + "-" + record.partition() + "-" + record.offset();
        if (processedEventRepository.existsByEventKey(eventKey)) {
            log.info("Duplicate event skipped: {}", eventKey);
            return;
        }

        ResultCreatedEvent event = fromJson(record.value());
        String notificationStatus;

        if ("FLAGGED".equals(event.status())) {
            try {
                notificationManager.sendFraudAlert(event);
                notificationStatus = "SENT";
            } catch (Exception ex) {
                log.error("Failed to send fraud alert for tx#{}: {}", event.transactionId(), ex.getMessage());
                failedNotificationService.queueForRetry(event, "EMAIL", ex);
                notificationStatus = "DEFERRED";
            }
        } else {
            log.info("Transaction #{} approved, no alert needed", event.transactionId());
            notificationStatus = "SKIPPED";
        }

        var processedEvent = new ProcessedEvent();
        processedEvent.setEventKey(eventKey);
        processedEvent.setProcessedAt(Instant.now());
        processedEventRepository.save(processedEvent);

        notificationCompletedPublisher.publish(new NotificationCompletedEvent(
                event.transactionId(),
                "NOTIFICATION",
                notificationStatus,
                Instant.now()));
    }

    private ResultCreatedEvent fromJson(String json) {
        try {
            return objectMapper.readValue(json, ResultCreatedEvent.class);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to deserialize ResultCreatedEvent", ex);
        }
    }
}
