package com.devblo.fraudanalysisservice.services.impl;

import com.devblo.fraudanalysisservice.events.TransactionCreatedEvent;
import com.devblo.fraudanalysisservice.models.FraudScore;
import com.devblo.fraudanalysisservice.services.IFraudAnalysisService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class FraudAnalysisServiceImpl implements IFraudAnalysisService {

    private static final BigDecimal HIGH_AMOUNT = new BigDecimal("10000");
    private static final Set<String> HIGH_RISK_COUNTRIES = Set.of("XX", "YY", "ZZ");

    @Override
    public FraudScore analyze(TransactionCreatedEvent event) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        if (event.amount().compareTo(HIGH_AMOUNT) > 0) {
            score += 70;
            reasons.add("High amount: " + event.amount());
        }

        if (HIGH_RISK_COUNTRIES.contains(event.country())) {
            score += 80;
            reasons.add("High-risk country: " + event.country());
        }

        // Add more rules: velocity check, merchant category, time of day, etc.

        return new FraudScore(event.transactionId(), score,
                score >= 70 ? "FLAGGED" : "APPROVED", reasons);
    }
}
