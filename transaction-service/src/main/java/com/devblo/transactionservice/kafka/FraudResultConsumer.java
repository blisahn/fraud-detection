package com.devblo.transactionservice.kafka;

import com.devblo.transactionservice.dtos.fraudScore.FraudScore;
import com.devblo.transactionservice.entity.enums.TransactionStatus;
import com.devblo.transactionservice.services.ITransactionService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class FraudResultConsumer {
    private static final Logger log = LoggerFactory.getLogger(FraudResultConsumer.class);

    private final ITransactionService transactionService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "fraud-results", groupId = "transaction-service")
    public void handleFraudResult(ConsumerRecord<String, String> record) {
        FraudScore score = fromJson(record.value());
        TransactionStatus newStatus = switch (score.status()) {
            case "FLAGGED" -> TransactionStatus.FLAGGED;
            case "APPROVED" -> TransactionStatus.APPROVED;
            default -> TransactionStatus.PENDING;
        };

        transactionService.updateStatus(score.transactionId(), newStatus);
        log.info("Transaction {} updated to {} (fraud score: {}, reasons: {})",
                score.transactionId(), newStatus, score.score(), score.reasons());
    }

    private FraudScore fromJson(String json) {
        try {
            return objectMapper.readValue(json, FraudScore.class);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to deserialize FraudScore", ex);
        }
    }
}
