package com.devblo.aiinsightservice.repositories;

import com.devblo.aiinsightservice.entity.FraudSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IFraudSnapshotRepository extends JpaRepository<FraudSnapshot, Long> {
}
