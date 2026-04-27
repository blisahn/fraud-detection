package com.devblo.fraudanalysisservice.kafka;

import com.devblo.fraudanalysisservice.models.FraudScore;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class FraudResultPublisher {
    private static final Logger log = LoggerFactory.getLogger(FraudResultPublisher.class);
    private static final String TOPIC = "fraud-results";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publish(FraudScore score) {
        String key = score.transactionId().toString();
        String payload = toJson(score);

        kafkaTemplate.send(TOPIC, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed  to publish fraud result for txn {}: {}", score.transactionId(), ex.getMessage());
                    } else {
                        var metadata = result.getRecordMetadata();
                        log.info("Published fraud result for txn {} -> partition={} offset={}",
                                score.transactionId(), metadata.partition(), metadata.offset());
                    }
                });
    }

    private String toJson(FraudScore score) {
        try {
            return objectMapper.writeValueAsString(score);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize FraudScore", e);
        }
    }
}
