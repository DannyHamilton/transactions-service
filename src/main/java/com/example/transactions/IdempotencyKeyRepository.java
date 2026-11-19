package com.example.transactions;

import org.springframework.data.jpa.repository.JpaRepository;

interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {
}
