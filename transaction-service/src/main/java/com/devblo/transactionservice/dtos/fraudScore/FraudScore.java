package com.devblo.transactionservice.dtos.fraudScore;

import java.util.List;

public record FraudScore(Long transactionId, int score, String status, List<String> reasons) {
}
