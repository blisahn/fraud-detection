package com.devblo.transactionservice.config;

import com.devblo.transactionservice.entity.OutboxEvent;
import com.devblo.transactionservice.entity.Transaction;
import com.devblo.transactionservice.entity.enums.TransactionStatus;
import com.devblo.transactionservice.repositories.IOutboxRepository;
import com.devblo.transactionservice.services.ITransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;


@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private final IOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPoller(IOutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxRepository
                .findTop100ByPublishedFalseOrderByCreatedAtAsc();

        for (OutboxEvent event : events) {
            String topic = event.getAggregateType().toLowerCase() + "-events";

            kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload())
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            event.setPublished(true);
                            outboxRepository.save(event);
                            log.info("Outbox event published {}: {}", event.getId(), event.getPayload());
                        } else {
                            log.error("Failed to publish outbox event {}: {}", event.getId(), ex.getMessage());
                        }
                    });
        }
    }


}
