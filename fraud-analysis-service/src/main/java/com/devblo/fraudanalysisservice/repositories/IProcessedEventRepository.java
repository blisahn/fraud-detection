package com.devblo.fraudanalysisservice.repositories;

import com.devblo.fraudanalysisservice.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;


public interface IProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
    boolean existsByEventKey(String eventKey);
}

