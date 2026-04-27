package com.devblo.notificationservice.repositories;


import com.devblo.notificationservice.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;


public interface IProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
    boolean existsByEventKey(String eventKey);
}

