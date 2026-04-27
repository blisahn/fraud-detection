package com.devblo.aiinsightservice.repositories;

import com.devblo.aiinsightservice.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface IProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
    boolean existsByEventKey(String eventKey);

    List<ProcessedEvent> getProcessedEventByEventKey(String eventKey);
}

