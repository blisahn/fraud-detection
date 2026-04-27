package com.devblo.notificationservice.repositories;

import com.devblo.notificationservice.entity.FailedNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface IFailedNotificationRepository extends JpaRepository<FailedNotification, Long> {

    @Query("""
            select f from FailedNotification f
             where f.status = 'PENDING'
               and f.nextAttemptAt <= :now
             order by f.nextAttemptAt asc
            """)
    List<FailedNotification> findDueForRetry(@Param("now") Instant now, Pageable pageable);
}
