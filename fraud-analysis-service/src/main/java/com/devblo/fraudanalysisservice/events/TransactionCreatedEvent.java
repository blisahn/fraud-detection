package com.devblo.fraudanalysisservice.events;

import java.math.BigDecimal;

public record TransactionCreatedEvent(Long transactionId,
                                      Long accountId,
                                      BigDecimal amount,
                                      String currency,
                                      String country,
                                      String merchantName
) {
}
