package com.devblo.fraudanalysisservice.models;

import java.util.List;

public record FraudScore(Long transactionId,
                         int score,
                         String status,
                         List<String> reasons) {
}
