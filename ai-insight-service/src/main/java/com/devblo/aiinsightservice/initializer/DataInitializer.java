package com.devblo.aiinsightservice.initializer;

import com.devblo.aiinsightservice.ai.FraudKnowledgeIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final FraudKnowledgeIngestionService ingestionService;

    @Override
    public void run(String... args) {
        ingestionService.ingestIfEmpty();
    }
}

