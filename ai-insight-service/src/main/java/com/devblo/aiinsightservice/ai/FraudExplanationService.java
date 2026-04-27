package com.devblo.aiinsightservice.ai;

import com.devblo.aiinsightservice.entity.FraudSnapshot;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class FraudExplanationService {
    private final ChatClient chat;


    private final VectorStore vectorStore;

    public FraudExplanationService(ChatClient.Builder builder, VectorStore vectorStore) {
        this.chat = builder.defaultSystem("""
                You are a fraud analysis assistant. Use ONLY the provided context.
                Never invent rule IDs. If the context is insufficient, say so.
                Respond in JSON with keys: summary, rulesTriggered (array of {id, name, why}),
                riskAssessment, recommendedAction.
                """).build();
        this.vectorStore = vectorStore;
    }



    public ExplanationResponse explain(FraudSnapshot snap) {
        String query = "Amount %s %s from country %s at merchant %s flagged score %d reasons %s"
                .formatted(snap.getAmount(), snap.getCurrency(), snap.getCountry(),
                        snap.getMerchantName(), snap.getScore(),
                        String.join("; ", Optional.ofNullable(snap.getReasons()).orElse(List.of())));

        List<Document> context = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(5).similarityThreshold(0.5).build()
        );

        String contextText = context.stream()
                .map(d -> "[" + d.getMetadata() + "] " + d.getText())
                .collect(Collectors.joining("\n\n"));

        ExplanationBody body = chat.prompt()
                .user(u -> u.text("""
                                Transaction:
                                - id: {id}
                                - account: {acc}
                                - amount: {amt} {cur}
                                - country: {ctry}
                                - merchant: {mer}
                                - score: {score}
                                - status: {status}
                                - raw reasons from rule engine: {reasons}
                                
                                Context:
                                {ctx}
                                """)
                        .param("id", snap.getTransactionId())
                        .param("acc", snap.getAccountId())
                        .param("amt", snap.getAmount())
                        .param("cur", snap.getCurrency())
                        .param("ctry", snap.getCountry())
                        .param("mer", snap.getMerchantName())
                        .param("score", snap.getScore())
                        .param("status", snap.getStatus())
                        .param("reasons", snap.getReasons())
                        .param("ctx", contextText))
                .call()
                .entity(ExplanationBody.class);

        List<String> sourceIds = context.stream()
                .map(d -> String.valueOf(d.getMetadata().getOrDefault("ruleId",
                        d.getMetadata().getOrDefault("caseId",
                                d.getMetadata().getOrDefault("docId", "unknown"))))).toList();

        return new ExplanationResponse(snap.getTransactionId(), body, sourceIds);
    }

    public record RuleHit(String id, String name, String why) {
    }

    public record ExplanationBody(String summary, List<RuleHit> rulesTriggered,
                                  String riskAssessment, String recommendedAction) {
    }

    public record ExplanationResponse(Long transactionId, ExplanationBody body, List<String> sourceIds) {
    }
}
