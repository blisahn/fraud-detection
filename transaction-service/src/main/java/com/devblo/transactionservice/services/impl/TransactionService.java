package com.devblo.transactionservice.services.impl;

import com.devblo.transactionservice.dtos.transaction.CreateTransactionRequest;
import com.devblo.transactionservice.entity.OutboxEvent;
import com.devblo.transactionservice.entity.Transaction;
import com.devblo.transactionservice.entity.enums.TransactionStatus;
import com.devblo.transactionservice.events.TransactionCreatedEvent;
import com.devblo.transactionservice.repositories.IOutboxRepository;
import com.devblo.transactionservice.repositories.ITransactionRepository;
import com.devblo.transactionservice.services.ITransactionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService implements ITransactionService {
    private final ITransactionRepository transactionRepository;
    private final IOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private Counter transactionsCreated;
    private Timer sagaCompletionTime;

    @PostConstruct
    void initMetrics() {
        this.transactionsCreated = Counter.builder("fraud.transactions.created.total")
                .description("Total transactions created")
                .register(meterRegistry);

        this.sagaCompletionTime = Timer.builder("fraud.saga.completion.duration")
                .description("Time fromm transaction creation to saga completion")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    @Override
    @Transactional
    public Transaction createTransaction(CreateTransactionRequest req) {

        Transaction tx = new Transaction();
        tx.setAccountId(req.accountId());
        tx.setAmount(req.amount());
        tx.setMerchantName(req.merchantName());
        tx.setCountry(req.country());
        tx.setCurrency(req.currency());
        tx.setStatus(TransactionStatus.PENDING);
        transactionRepository.save(tx);

        TransactionCreatedEvent event = new TransactionCreatedEvent(
                tx.getId(), tx.getAccountId(), tx.getAmount(),
                tx.getCurrency(), tx.getCountry(), tx.getMerchantName()
        );

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateType("Transaction");
        outboxEvent.setAggregateId(tx.getId().toString());
        outboxEvent.setEventType("TransactionCreated");
        outboxEvent.setPayload(toJson(event));
        outboxRepository.save(outboxEvent);

        transactionsCreated.increment();
        return tx;
    }

    private String toJson(TransactionCreatedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
    }

    @Override
    @Transactional
    public void updateStatus(Long transactionId, TransactionStatus newStatus) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found: " + transactionId));

        if (tx.getStatus() == TransactionStatus.PENDING) {
            long durationMs = Instant.now().toEpochMilli() - tx.getCreatedAt().toEpochMilli();
            sagaCompletionTime.record(durationMs, TimeUnit.MILLISECONDS);
        }
        tx.setStatus(newStatus);
        transactionRepository.save(tx);

        if (newStatus == TransactionStatus.FLAGGED) {
            Counter.builder("fraud.transactions.flagged.total")
                    .register(meterRegistry)
                    .increment();
        }
    }

    @Override
    public Optional<Transaction> findById(Long id) {
        return transactionRepository.findById(id);
    }

    @Override
    public List<Transaction> findAll() {
        return transactionRepository.findAll();
    }

    @Override
    @Transactional
    public void checkStaleTransactions(TransactionStatus oldStatus, Instant threshold, TransactionStatus newStatus) {
        int updatedCount = transactionRepository.updateStaleTransactionsStatus(
                oldStatus,
                newStatus,
                threshold
        );
        if (updatedCount > 0) {
            log.warn("{} transactions timed out waiting for fraud analysis and updated to REVIEW_NEEDED", updatedCount);
        }
    }


}
