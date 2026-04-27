package com.devblo.fraudanalysisservice.kafka;

import com.devblo.fraudanalysisservice.entity.ProcessedEvent;
import com.devblo.fraudanalysisservice.events.TransactionCreatedEvent;
import com.devblo.fraudanalysisservice.models.FraudScore;
import com.devblo.fraudanalysisservice.repositories.IProcessedEventRepository;
import com.devblo.fraudanalysisservice.services.IFraudAnalysisService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
    private final IFraudAnalysisService fraudAnalysisService;
    private final FraudResultPublisher fraudResultPublisher;
    private final IProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @KafkaListener(topics = "transaction-events", groupId = "fraud-analysis")
    @Transactional
    public void handleTransactionCreated(ConsumerRecord<String, String> record) {
        // Idempotency check
        String eventKey = record.topic() + "-" + record.partition() + "-" + record.offset();
        if (processedEventRepository.existsByEventKey(eventKey)) {
            log.info("Duplicate event skipped: {}", eventKey);
            return;
        }

        // Payload deserialization
        TransactionCreatedEvent event = fromJson(record.value());
        log.info("Processing transaction {} from account {}", event.transactionId(), event.accountId());
        // Fraud Analysis
        FraudScore score = fraudAnalysisService.analyze(event);
        log.info("Transaction {} scored: {} ({}) ", event.transactionId(), score.score(), event.accountId());

        Counter.builder("fraud.analysis.processed.total")
                .tag("decision", score.status())
                .register(meterRegistry)
                .increment();
        // Publish score
        fraudResultPublisher.publish(score);
        var eventToSave = new ProcessedEvent();
        eventToSave.setEventKey(eventKey);
        eventToSave.setProcessedAt(Instant.now());
        processedEventRepository.save(eventToSave);
    }

    private TransactionCreatedEvent fromJson(String json) {
        try {
            return objectMapper.readValue(json, TransactionCreatedEvent.class);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to deserialize TransactionCreatedEvent", ex);
        }
    }
}
