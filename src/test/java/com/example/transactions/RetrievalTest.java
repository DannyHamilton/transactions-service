package com.example.transactions;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RetrievalTest extends ApiTestBase {

    @Test
    void getReturnsTheTransaction() {
        JsonNode created = createPending(uniquePlayer());

        Reply r = get("/transactions/" + created.get("transactionId").asText());

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.json()).isEqualTo(created);
    }

    @Test
    void getUnknownIdReturns404ProblemJson() {
        Reply r = get("/transactions/" + UUID.randomUUID());

        assertThat(r.status()).isEqualTo(404);
        assertThat(r.contentType()).startsWith("application/problem+json");
    }

    @Test
    void getMalformedIdReturns400() {
        assertThat(get("/transactions/not-a-uuid").status()).isEqualTo(400);
    }

    @Test
    void listFiltersByPlayerAndStatus() {
        String player = uniquePlayer();
        String other = uniquePlayer();
        String pendingId = createPending(player).get("transactionId").asText();
        String cancelledId = createPending(player).get("transactionId").asText();
        createPending(other);
        assertThat(patch(cancelledId, "{\"status\":\"CANCELLED\"}").status()).isEqualTo(200);

        assertThat(listFor(player)).hasSize(2);
        JsonNode pending = get("/transactions?playerId=" + player + "&status=PENDING").json();
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).get("transactionId").asText()).isEqualTo(pendingId);
    }

    @Test
    void listWithoutFiltersIncludesNewTransactions() {
        String id = createPending(uniquePlayer()).get("transactionId").asText();

        Reply r = get("/transactions");

        assertThat(r.status()).isEqualTo(200);
        List<String> ids = new ArrayList<>();
        r.json().forEach(n -> ids.add(n.get("transactionId").asText()));
        assertThat(ids).contains(id);
    }

    @Test
    void listIsOrderedByCreatedAtThenTransactionId() {
        String player = uniquePlayer();
        for (int i = 0; i < 6; i++) {
            createPending(player);
        }

        JsonNode list = listFor(player);

        assertThat(list).hasSize(6);
        List<JsonNode> items = new ArrayList<>();
        list.forEach(items::add);
        Comparator<JsonNode> expected = Comparator
                .<JsonNode, Instant>comparing(n -> Instant.parse(n.get("createdAt").asText()))
                .thenComparing(n -> n.get("transactionId").asText());
        assertThat(items).isSortedAccordingTo(expected);
    }

    @Test
    void invalidStatusFilterReturns400() {
        assertThat(get("/transactions?status=BOGUS").status()).isEqualTo(400);
    }
}
