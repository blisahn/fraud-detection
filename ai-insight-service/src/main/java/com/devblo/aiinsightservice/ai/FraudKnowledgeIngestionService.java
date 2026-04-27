package com.devblo.aiinsightservice.ai;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FraudKnowledgeIngestionService {
    private static final Logger log = LoggerFactory.getLogger(FraudKnowledgeIngestionService.class);
    private final VectorStore vectorStore;

    public void ingestIfEmpty() {
        List<Document> probe = vectorStore.similaritySearch(
                SearchRequest.builder().query("probe").topK(1).build());
        if (!probe.isEmpty()) {
            log.info("Vector store already seeded.");
            return;
        }

        List<Document> docs = List.of(
                Document.builder().text("""
                        RULE-001 High-value transaction: Any transaction exceeding $10,000 USD equivalent
                        is automatically flagged. Applies to single and cumulative 24-hour amounts.
                        """).metadata(Map.of("category", "amount-rules", "ruleId", "RULE-001", "severity", "HIGH")).build(),
                Document.builder().text("""
                        RULE-002 High-risk country: Transactions originating from or destined to country
                        codes XX, YY, ZZ require manual review.
                        """).metadata(Map.of("category", "geo-rules", "ruleId", "RULE-002", "severity", "HIGH")).build(),
                Document.builder().text("""
                        RULE-003 Velocity check: More than 5 transactions from the same account within
                        10 minutes triggers a velocity alert (automated card testing pattern).
                        """).metadata(Map.of("category", "velocity-rules", "ruleId", "RULE-003", "severity", "MEDIUM")).build(),
                Document.builder().text("""
                        RULE-004 Merchant category mismatch: Transactions at high-risk categories
                        (gambling, crypto, money transfer) from accounts with no prior history there.
                        """).metadata(Map.of("category", "merchant-rules", "ruleId", "RULE-004", "severity", "MEDIUM")).build(),
                Document.builder().text("""
                        RULE-005 Cross-border anomaly: A transaction in a country different from the
                        customer's registered country exceeding $1,000 — especially with impossible
                        travel time between consecutive transactions.
                        """).metadata(Map.of("category", "geo-rules", "ruleId", "RULE-005", "severity", "HIGH")).build(),
                Document.builder().text("""
                        CASE-2025-Q3-001: Fraud ring using stolen cards at electronics retailers in
                        country XX. Amounts between $8,000 and $15,000. Merchants with 'Electronics'
                        or 'Tech' in name. Accounts created within 30 days.
                        """).metadata(Map.of("category", "past-cases", "caseId", "CASE-2025-Q3-001")).build(),
                Document.builder().text("""
                        PROC-001 Resolution procedure for flagged transactions:
                        1) Automated rules compute initial score.
                        2) Score > 70 escalates to fraud team.
                        3) Analyst reviews context, history, merchant reputation.
                        4) Decision: approve, reject, or request additional verification.
                        """).metadata(Map.of("category", "procedures", "docId", "PROC-001")).build()
        );
        vectorStore.add(docs);
        log.info("Seeded {} fraud knowledge documents.", docs.size());
    }
}