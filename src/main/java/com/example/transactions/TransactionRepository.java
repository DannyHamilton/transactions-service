package com.example.transactions;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    // One atomic statement: the WHERE clause is the lifecycle rule, and a row count of 0 means the loser.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Transaction t
               set t.status = :status, t.winAmount = :winAmount
             where t.id = :id and t.status = :pending""")
    int settleIfPending(UUID id, TransactionStatus status, BigDecimal winAmount, TransactionStatus pending);
}
