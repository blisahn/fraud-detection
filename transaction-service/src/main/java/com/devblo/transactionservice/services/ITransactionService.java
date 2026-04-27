package com.devblo.transactionservice.services;

import aj.org.objectweb.asm.commons.Remapper;
import com.devblo.transactionservice.dtos.transaction.CreateTransactionRequest;
import com.devblo.transactionservice.entity.Transaction;
import com.devblo.transactionservice.entity.enums.TransactionStatus;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ITransactionService {
    Transaction createTransaction(CreateTransactionRequest req);

    void updateStatus(Long transactionId, TransactionStatus newStatus);

    Optional<Transaction> findById(Long id);

    List<Transaction> findAll();

    void checkStaleTransactions(
            TransactionStatus oldStatus,
            Instant threshold,
            TransactionStatus newStatus);
}
