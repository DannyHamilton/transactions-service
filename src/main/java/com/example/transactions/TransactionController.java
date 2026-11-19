package com.example.transactions;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/transactions")
class TransactionController {

    private final TransactionService service;

    TransactionController(TransactionService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<String> create(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey,
            @Valid @RequestBody CreateTransactionRequest request) {
        TransactionService.StoredResponse response = service.create(idempotencyKey, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}").buildAndExpand(response.transactionId()).toUri();
        return ResponseEntity.status(response.status())
                .location(location)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response.body());
    }

    @GetMapping("/{id}")
    TransactionResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping
    List<TransactionResponse> list(@RequestParam(required = false) String playerId,
            @RequestParam(required = false) TransactionStatus status) {
        return service.list(playerId, status);
    }

    @PatchMapping("/{id}")
    TransactionResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTransactionRequest request) {
        return service.update(id, request);
    }
}
