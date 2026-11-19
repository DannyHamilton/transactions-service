package com.example.transactions;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Black-box HTTP tests against the real server and H2. Uses java.net.http.HttpClient because
 * HttpURLConnection (behind TestRestTemplate's default factory) cannot send PATCH.
 * Tests share one database, so each test isolates itself with a unique playerId.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class ApiTestBase {

    static final ObjectMapper JSON =
            new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    static final HttpClient HTTP =
            HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @LocalServerPort
    int port;

    record Reply(int status, String contentType, String body) {
        JsonNode json() {
            try {
                return JSON.readTree(body);
            } catch (JsonProcessingException e) {
                return MissingNode.getInstance();
            }
        }
    }

    Reply post(String idempotencyKey, String body) {
        HttpRequest.Builder b = request("/transactions")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (idempotencyKey != null) {
            b.header("Idempotency-Key", idempotencyKey);
        }
        return send(b);
    }

    Reply patch(String id, String body) {
        return send(request("/transactions/" + id)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body)));
    }

    Reply get(String path) {
        return send(request(path).GET());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    private Reply send(HttpRequest.Builder builder) {
        try {
            HttpResponse<String> r = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Reply(r.statusCode(), r.headers().firstValue("Content-Type").orElse(""), r.body());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    static String uniquePlayer() {
        return "player-" + UUID.randomUUID();
    }

    static String newKey() {
        return UUID.randomUUID().toString();
    }

    /** Amounts are passed as raw JSON number literals so tests can send e.g. 10.500. */
    static String body(String playerId, String betAmount) {
        return body("round-1", playerId, "Blackjack", betAmount, "USD");
    }

    static String body(String roundId, String playerId, String gameName, String betAmount, String currency) {
        return """
                {"roundId":"%s","playerId":"%s","gameName":"%s","betAmount":%s,"currency":"%s"}"""
                .formatted(roundId, playerId, gameName, betAmount, currency);
    }

    /** Creates a PENDING transaction for the player and returns the 201 response body. */
    JsonNode createPending(String playerId) {
        Reply r = post(newKey(), body(playerId, "10.00"));
        assertThat(r.status()).as("create: %s", r.body()).isEqualTo(201);
        return r.json();
    }

    JsonNode listFor(String playerId) {
        Reply r = get("/transactions?playerId=" + playerId);
        assertThat(r.status()).as("list: %s", r.body()).isEqualTo(200);
        return r.json();
    }

    static List<Reply> runConcurrently(List<Callable<Reply>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Reply>> futures = new ArrayList<>();
            for (Callable<Reply> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Reply> replies = new ArrayList<>();
            for (Future<Reply> f : futures) {
                replies.add(f.get(30, TimeUnit.SECONDS));
            }
            return replies;
        } finally {
            pool.shutdownNow();
        }
    }
}
