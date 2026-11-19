package com.example.transactions;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CreateValidationTest extends ApiTestBase {

    @Test
    void createReturns201WithPendingTransaction() {
        String player = uniquePlayer();

        Reply r = post(newKey(), body("round-7", player, "Roulette", "25.50", "EUR"));

        assertThat(r.status()).as(r.body()).isEqualTo(201);
        var t = r.json();
        assertThat(UUID.fromString(t.get("transactionId").asText()).version()).isEqualTo(7);
        assertThat(t.get("roundId").asText()).isEqualTo("round-7");
        assertThat(t.get("playerId").asText()).isEqualTo(player);
        assertThat(t.get("gameName").asText()).isEqualTo("Roulette");
        assertThat(t.get("betAmount").decimalValue()).isEqualByComparingTo("25.50");
        assertThat(t.get("currency").asText()).isEqualTo("EUR");
        assertThat(t.get("status").asText()).isEqualTo("PENDING");
        assertThat(t.get("winAmount").isNull()).isTrue();
        assertThat(Instant.parse(t.get("createdAt").asText())).isNotNull();
    }

    @Test
    void missingIdempotencyKeyReturns400() {
        Reply r = post(null, body(uniquePlayer(), "10.00"));

        assertThat(r.status()).isEqualTo(400);
        assertThat(r.contentType()).startsWith("application/problem+json");
    }

    @Test
    void blankIdempotencyKeyReturns400() {
        assertThat(post("   ", body(uniquePlayer(), "10.00")).status()).isEqualTo(400);
    }

    @Test
    void idempotencyKeyLongerThan255CharactersReturns400() {
        assertThat(post("k".repeat(256), body(uniquePlayer(), "10.00")).status()).isEqualTo(400);
    }

    @Test
    void idempotencyKeyOf255CharactersIsAccepted() {
        assertThat(post("k".repeat(255), body(uniquePlayer(), "10.00")).status()).isEqualTo(201);
    }

    // PLAYER is replaced with a unique player id so any accidental creation is isolated.
    @ParameterizedTest
    @ValueSource(strings = {
        "{'playerId':'PLAYER','gameName':'G','betAmount':10.00,'currency':'USD'}",
        "{'roundId':'r','gameName':'G','betAmount':10.00,'currency':'USD'}",
        "{'roundId':'r','playerId':'PLAYER','betAmount':10.00,'currency':'USD'}",
        "{'roundId':'r','playerId':'PLAYER','gameName':'G','currency':'USD'}",
        "{'roundId':'r','playerId':'PLAYER','gameName':'G','betAmount':10.00}",
        "{'roundId':'r','playerId':'PLAYER','gameName':'G','betAmount':0,'currency':'USD'}",
        "{'roundId':'r','playerId':'PLAYER','gameName':'G','betAmount':-5.00,'currency':'USD'}",
        "{'roundId':'r','playerId':'PLAYER','gameName':'G','betAmount':10.001,'currency':'USD'}",
        "{'roundId':'r','playerId':'PLAYER','gameName':'G','betAmount':'abc','currency':'USD'}",
        "{'roundId':'r','playerId':'PLAYER','gameName':'G','betAmount':10.00,'currency':'usd'}",
        "{'roundId':'r','playerId':'PLAYER','gameName':'G','betAmount':10.00,'currency':'US'}",
        "{'roundId':'r','playerId':'PLAYER','gameName':'G','betAmount':10.00,'currency':'ZZZ'}",
        "{'roundId':'r','playerId':'PLAYER','gameName':'G','betAmount':99999999999999999999.00,'currency':'USD'}",
        "not json"
    })
    void invalidBodyReturns400ProblemJsonAndCreatesNothing(String template) {
        String player = uniquePlayer();
        String payload = template.replace('\'', '"').replace("PLAYER", player);

        Reply r = post(newKey(), payload);

        assertThat(r.status()).as(r.body()).isEqualTo(400);
        assertThat(r.contentType()).startsWith("application/problem+json");
        assertThat(listFor(player)).isEmpty();
    }

    @Test
    void trailingZerosBeyondTwoDecimalsAreAcceptedWhenValueIsStillTwoDecimals() {
        Reply r = post(newKey(), body(uniquePlayer(), "10.500"));

        assertThat(r.status()).as(r.body()).isEqualTo(201);
        assertThat(r.json().get("betAmount").decimalValue()).isEqualByComparingTo("10.5");
    }
}
