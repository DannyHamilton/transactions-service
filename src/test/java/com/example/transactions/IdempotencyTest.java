package com.example.transactions;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdempotencyTest extends ApiTestBase {

    @Test
    void repeatedPostReplaysOriginalResponseAndCreatesNothingNew() {
        String player = uniquePlayer();
        String key = newKey();
        String body = body(player, "10.00");

        Reply first = post(key, body);
        Reply second = post(key, body);

        assertThat(first.status()).as(first.body()).isEqualTo(201);
        assertThat(second.status()).isEqualTo(201);
        assertThat(second.body()).isEqualTo(first.body());
        assertThat(listFor(player)).hasSize(1);
    }

    @Test
    void replayReturnsStoredResponseEvenAfterTransactionIsSettled() {
        String player = uniquePlayer();
        String key = newKey();
        String body = body(player, "10.00");
        Reply first = post(key, body);
        String id = first.json().get("transactionId").asText();
        assertThat(patch(id, "{\"status\":\"COMPLETED\",\"winAmount\":5.00}").status()).isEqualTo(200);

        Reply replay = post(key, body);

        assertThat(replay.status()).isEqualTo(201);
        assertThat(replay.body()).isEqualTo(first.body());
        assertThat(replay.json().get("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void sameKeyWithDifferentBodyConflictsAndProcessesNothing() {
        String player = uniquePlayer();
        String key = newKey();
        assertThat(post(key, body(player, "10.00")).status()).isEqualTo(201);

        Reply conflicting = post(key, body(player, "99.00"));

        assertThat(conflicting.status()).isEqualTo(409);
        assertThat(conflicting.contentType()).startsWith("application/problem+json");
        assertThat(listFor(player)).hasSize(1);
    }

    @Test
    void keyIsGloballyUniqueAcrossPlayers() {
        String key = newKey();
        String firstPlayer = uniquePlayer();
        String otherPlayer = uniquePlayer();
        assertThat(post(key, body(firstPlayer, "10.00")).status()).isEqualTo(201);

        assertThat(post(key, body(otherPlayer, "10.00")).status()).isEqualTo(409);
        assertThat(listFor(otherPlayer)).isEmpty();
    }

    @Test
    void numericallyEqualAmountsAreTheSameRequest() {
        String player = uniquePlayer();
        String key = newKey();
        Reply first = post(key, body(player, "10.5"));

        Reply second = post(key, body(player, "10.50"));
        Reply third = post(key, body(player, "10.500"));

        assertThat(first.status()).as(first.body()).isEqualTo(201);
        assertThat(second.status()).isEqualTo(201);
        assertThat(second.body()).isEqualTo(first.body());
        assertThat(third.status()).isEqualTo(201);
        assertThat(listFor(player)).hasSize(1);
    }

    @Test
    void propertyOrderAndWhitespaceDoNotMatter() {
        String player = uniquePlayer();
        String key = newKey();
        Reply first = post(key, body(player, "10.00"));
        String reordered = """
                {
                  "currency": "USD",
                  "betAmount": 10.00,
                  "gameName": "Blackjack",
                  "playerId": "%s",
                  "roundId": "round-1"
                }""".formatted(player);

        Reply second = post(key, reordered);

        assertThat(second.status()).as(second.body()).isEqualTo(201);
        assertThat(second.body()).isEqualTo(first.body());
        assertThat(listFor(player)).hasSize(1);
    }

    @Test
    void invalidRequestDoesNotConsumeTheKey() {
        String player = uniquePlayer();
        String key = newKey();
        assertThat(post(key, body(player, "0")).status()).isEqualTo(400);

        Reply valid = post(key, body(player, "10.00"));

        assertThat(valid.status()).as(valid.body()).isEqualTo(201);
    }
}
