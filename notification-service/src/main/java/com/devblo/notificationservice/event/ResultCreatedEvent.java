package com.devblo.notificationservice.event;

import java.util.List;

public record ResultCreatedEvent(Long transactionId,
                                 int score,
                                 String status,
                                 List<String> reasons) {
}
