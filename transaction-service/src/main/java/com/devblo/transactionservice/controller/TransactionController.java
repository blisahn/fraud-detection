package com.devblo.transactionservice.controller;

import com.devblo.transactionservice.dtos.transaction.CreateTransactionRequest;
import com.devblo.transactionservice.entity.Transaction;
import com.devblo.transactionservice.services.ITransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {
    private final ITransactionService transactionService;

    @PostMapping
    public ResponseEntity<Transaction> create(@Valid @RequestBody CreateTransactionRequest request) {
        Transaction saved = transactionService.createTransaction(request);
        return ResponseEntity
                .created(URI.create("/api/v1/transactions/" + saved.getId()))
                .body(saved);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Transaction> getById(@PathVariable Long id) {
        return transactionService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<Transaction>> list() {
        return ResponseEntity.ok(transactionService.findAll());
    }
}
