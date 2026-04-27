package com.devblo.aiinsightservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Entity
@Table(name = "fraud_snapshots")
public class FraudSnapshot {

    @Id
    @Column(name = "transaction_id")
    private Long transactionId;

    private Long accountId;
    private BigDecimal amount;
    private String currency;
    private String country;
    private String merchantName;
    private Integer score;
    private String status;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> reasons;
    private Instant updatedAt = Instant.now();
}
