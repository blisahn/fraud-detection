package com.devblo.fraudanalysisservice.services;

import com.devblo.fraudanalysisservice.events.TransactionCreatedEvent;
import com.devblo.fraudanalysisservice.models.FraudScore;

public interface IFraudAnalysisService {
    FraudScore analyze(TransactionCreatedEvent event);
}
