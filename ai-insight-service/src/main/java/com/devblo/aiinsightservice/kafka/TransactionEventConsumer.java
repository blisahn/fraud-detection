package com.devblo.aiinsightservice.kafka;

import com.devblo.aiinsightservice.entity.FraudSnapshot;
import com.devblo.aiinsightservice.entity.ProcessedEvent;
import com.devblo.aiinsightservice.events.TransactionCreatedEvent;
import com.devblo.aiinsightservice.repositories.IFraudSnapshotRepository;
import com.devblo.aiinsightservice.repositories.IProcessedEventRepository;
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
public class TransactionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventConsumer.class);

    private final IProcessedEventRepository processedEventRepository;
    private final IFraudSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "transaction-events", groupId = "ai-insight")
    @Transactional
    public void handle(ConsumerRecord<String, String> record) {
        String eventKey = record.topic() + "-" + record.partition() + "-" + record.offset();
        if (processedEventRepository.existsByEventKey(eventKey)) {
            return;
        }
        TransactionCreatedEvent event = fromJson(record.value(), TransactionCreatedEvent.class);
        FraudSnapshot snapshot = snapshotRepository.findById(event.transactionId())
                .orElseGet(() -> {
                    FraudSnapshot s = new FraudSnapshot();
                    s.setTransactionId(event.transactionId());
                    return s;
                });
        snapshot.setAccountId(event.accountId());
        snapshot.setAmount(event.amount());
        snapshot.setCurrency(event.currency());
        snapshot.setCountry(event.country());
        snapshot.setMerchantName(event.merchantName());
        snapshot.setUpdatedAt(Instant.now());
        snapshotRepository.save(snapshot);

        markProcessed(eventKey);
    }

    private void markProcessed(String eventKey) {
        var processedEvent = new ProcessedEvent();
        processedEvent.setEventKey(eventKey);
        processedEvent.setProcessedAt(Instant.now());
        processedEventRepository.save(processedEvent);
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to deserialize ResultCreatedEvent", ex);
        }
    }
}
