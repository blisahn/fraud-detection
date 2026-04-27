package com.devblo.transactionservice.job;

import com.devblo.transactionservice.entity.enums.TransactionStatus;
import com.devblo.transactionservice.services.ITransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class SagaTimeoutJob {
    private static final Logger log = LoggerFactory.getLogger(SagaTimeoutJob.class);
    private final ITransactionService transactionService;

    public SagaTimeoutJob(ITransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Transactional
    @Scheduled(fixedDelay = 30000)
    public void checkPendingTransactions() {
        Instant threshold = Instant.now().minus(30, ChronoUnit.SECONDS);
        transactionService.checkStaleTransactions(TransactionStatus.PENDING, threshold, TransactionStatus.REVIEW_NEEDED);
    }
}
