package com.devblo.notificationservice.service;

import com.devblo.notificationservice.event.ResultCreatedEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service("EMAIL")
@RequiredArgsConstructor
public class EmailService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender mailSender;
    private final FailedNotificationService failedNotificationService;

    @Value("${notification.recipient-email}")
    private String recipientEmail;

    @Override
    @CircuitBreaker(name = "emailService", fallbackMethod = "sendFraudAlertFallback")
    @Retry(name = "emailService")
    public void sendFraudAlert(ResultCreatedEvent event) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipientEmail);
        message.setSubject("FRAUD ALERT - Transaction #" + event.transactionId());
        message.setText("""
                A transaction has been FLAGGED by the fraud detection system.

                Transaction ID: %d
                Fraud Score: %d
                Status: %s
                Reasons: %s

                Please review this transaction immediately.
                """.formatted(
                event.transactionId(),
                event.score(),
                event.status(),
                String.join(", ", event.reasons())
        ));

        mailSender.send(message);
        log.info("Fraud alert email sent for transaction #{}", event.transactionId());
    }

    @SuppressWarnings("unused")
    private void sendFraudAlertFallback(ResultCreatedEvent event, Throwable t) {
        failedNotificationService.queueForRetry(event, getType(), t);
    }

    @Override
    public String getType() {
        return "EMAIL";
    }
}
