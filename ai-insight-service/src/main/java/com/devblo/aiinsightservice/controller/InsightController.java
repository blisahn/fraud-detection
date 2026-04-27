package com.devblo.aiinsightservice.controller;

import com.devblo.aiinsightservice.ai.FraudExplanationService;
import com.devblo.aiinsightservice.repositories.IFraudSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("api/v1/insights")
@RequiredArgsConstructor
public class InsightController {
    private final IFraudSnapshotRepository snapshotRepository;
    private final FraudExplanationService fraudExplanationService;

    @GetMapping("/transaction/{id}")
    public ResponseEntity<?> explain(@PathVariable Long id) {
        return snapshotRepository.findById(id)
                .map(snap -> snap.getStatus() == null
                        ? ResponseEntity.accepted().body(Map.of(
                        "transactionId", id, "message", "Fraud result not yet received, try again"))
                        : ResponseEntity.ok(fraudExplanationService.explain(snap)))
                .orElse(ResponseEntity.notFound().build());
    }
}
