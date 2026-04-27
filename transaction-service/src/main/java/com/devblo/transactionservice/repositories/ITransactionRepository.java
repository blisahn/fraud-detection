package com.devblo.transactionservice.repositories;

import com.devblo.transactionservice.entity.Transaction;
import com.devblo.transactionservice.entity.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ITransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByAccountId(Long accountId);

    List<Transaction> findByStatusAndCreatedAtBefore(TransactionStatus status, Instant createdAtBefore);

    @Modifying
    @Query(value = "UPDATE Transaction t SET t.status = :newStatus WHERE t.status = :oldStatus AND t.createdAt < :threshold")
    int updateStaleTransactionsStatus(
            @Param("oldStatus") TransactionStatus oldStatus,
            @Param("newStatus") TransactionStatus newStatus,
            @Param("threshold") Instant threshold
    );
}
