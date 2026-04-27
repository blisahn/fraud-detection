package com.devblo.notificationservice.service;

import com.devblo.notificationservice.event.ResultCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@Qualifier("SMS")
@Slf4j
public class SmsService implements NotificationService {

    @Override
    public void sendFraudAlert(ResultCreatedEvent event) {
        // Placeholder implementation for SMS service
        // This would integrate with an SMS provider like Twilio, AWS SNS, etc.
        // For now, just log the alert
        log.info("SMS ALERT PLACEHOLDER - Fraud detected for transaction #{} with score {} and reasons: {}",
                event.transactionId(), event.score(), String.join(", ", event.reasons()));
        log.warn("SMS service is not implemented yet. This is a placeholder.");
    }

    @Override
    public String getType() {
        return "SMS";
    }
}