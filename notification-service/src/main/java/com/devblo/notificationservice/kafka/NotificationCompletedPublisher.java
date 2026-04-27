package com.devblo.notificationservice.kafka;

import com.devblo.notificationservice.event.NotificationCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class NotificationCompletedPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationCompletedPublisher.class);
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publish(NotificationCompletedEvent event) {
        String json = toJson(event);
        kafkaTemplate.send("notification-completed", event.transactionId().toString(), json)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish notification-completed for tx#{}: {}",
                                event.transactionId(), ex.getMessage());
                    } else {
                        log.info("Published notification-completed for tx#{}", event.transactionId());
                    }
                });
    }

    private String toJson(NotificationCompletedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to serialize NotificationCompletedEvent", ex);
        }
    }
}
