package com.example.transactions;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.RepeatedTest;

class ConcurrencyTest extends ApiTestBase {

    private static final int CALLERS = 12;

    @RepeatedTest(10)
    void concurrentPostsWithSameKeyCreateExactlyOneTransaction() throws Exception {
        String player = uniquePlayer();
        String key = newKey();
        String body = body(player, "10.00");

        List<Callable<Reply>> tasks = new ArrayList<>();
        for (int i = 0; i < CALLERS; i++) {
            tasks.add(() -> post(key, body));
        }
        List<Reply> replies = runConcurrently(tasks);

        // Every caller gets the original response, not a conflict.
        assertThat(replies).allSatisfy(r -> assertThat(r.status()).as(r.body()).isEqualTo(201));
        assertThat(replies.stream().map(Reply::body).distinct()).hasSize(1);
        assertThat(listFor(player)).hasSize(1);
    }

    @RepeatedTest(5)
    void concurrentPostsWithSameKeyAndDifferentBodiesYieldOneWinner() throws Exception {
        String player = uniquePlayer();
        String key = newKey();

        List<Callable<Reply>> tasks = new ArrayList<>();
        for (int i = 0; i < CALLERS; i++) {
            String bet = (i % 2 == 0) ? "10.00" : "20.00";
            tasks.add(() -> post(key, body(player, bet)));
        }
        List<Reply> replies = runConcurrently(tasks);

        assertThat(replies).allSatisfy(r -> assertThat(r.status()).as(r.body()).isIn(201, 409));
        List<Reply> created = replies.stream().filter(r -> r.status() == 201).toList();
        assertThat(created).isNotEmpty();
        assertThat(created.stream().map(Reply::body).distinct()).hasSize(1);
        assertThat(listFor(player)).hasSize(1);
    }

    @RepeatedTest(10)
    void concurrentCompleteAndCancelHaveExactlyOneWinner() throws Exception {
        String player = uniquePlayer();
        String id = createPending(player).get("transactionId").asText();

        List<Callable<Reply>> tasks = List.of(
                () -> patch(id, "{\"status\":\"COMPLETED\",\"winAmount\":12.50}"),
                () -> patch(id, "{\"status\":\"CANCELLED\"}"));
        List<Reply> replies = runConcurrently(tasks);

        assertThat(replies.stream().map(Reply::status).sorted().toList()).containsExactly(200, 409);
        Reply winner = replies.stream().filter(r -> r.status() == 200).findFirst().orElseThrow();

        JsonNode stored = get("/transactions/" + id).json();
        assertThat(stored.get("status").asText()).isEqualTo(winner.json().get("status").asText());
        if (stored.get("status").asText().equals("COMPLETED")) {
            assertThat(stored.get("winAmount").decimalValue()).isEqualByComparingTo("12.50");
        } else {
            assertThat(stored.get("winAmount").isNull()).isTrue();
        }
    }

    @RepeatedTest(5)
    void manyConcurrentPatchesWithDifferentOutcomesHaveExactlyOneWinner() throws Exception {
        String id = createPending(uniquePlayer()).get("transactionId").asText();

        List<Callable<Reply>> tasks = new ArrayList<>();
        for (int i = 0; i < CALLERS; i++) {
            String payload = (i % 3 == 0)
                    ? "{\"status\":\"CANCELLED\"}"
                    : "{\"status\":\"COMPLETED\",\"winAmount\":" + i + ".00}";
            tasks.add(() -> patch(id, payload));
        }
        List<Reply> replies = runConcurrently(tasks);

        List<Reply> winners = replies.stream().filter(r -> r.status() == 200).toList();
        assertThat(winners).hasSize(1);
        assertThat(replies.stream().filter(r -> r.status() == 409)).hasSize(CALLERS - 1);
        Reply winner = winners.get(0);

        JsonNode stored = get("/transactions/" + id).json();
        assertThat(stored.get("status")).isEqualTo(winner.json().get("status"));
        assertThat(stored.get("winAmount")).isEqualTo(winner.json().get("winAmount"));
    }
}
