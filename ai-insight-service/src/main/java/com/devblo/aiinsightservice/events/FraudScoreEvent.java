package com.devblo.aiinsightservice.events;

import java.util.List;

public record FraudScoreEvent(Long transactionId,
                              int score,
                              String status,
                              List<String> reasons) {
}
