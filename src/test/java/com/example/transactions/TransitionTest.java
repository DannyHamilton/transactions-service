package com.example.transactions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TransitionTest extends ApiTestBase {

    private static final String COMPLETE = "{\"status\":\"COMPLETED\",\"winAmount\":12.50}";
    private static final String CANCEL = "{\"status\":\"CANCELLED\"}";

    private String newPendingId() {
        return createPending(uniquePlayer()).get("transactionId").asText();
    }

    @Test
    void pendingTransactionCanBeCompletedWithWinAmount() {
        String id = newPendingId();

        Reply r = patch(id, COMPLETE);

        assertThat(r.status()).as(r.body()).isEqualTo(200);
        assertThat(r.json().get("status").asText()).isEqualTo("COMPLETED");
        assertThat(r.json().get("winAmount").decimalValue()).isEqualByComparingTo("12.50");
        assertThat(get("/transactions/" + id).json().get("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void zeroWinAmountIsAllowed() {
        Reply r = patch(newPendingId(), "{\"status\":\"COMPLETED\",\"winAmount\":0.00}");

        assertThat(r.status()).as(r.body()).isEqualTo(200);
        assertThat(r.json().get("winAmount").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void pendingTransactionCanBeCancelledWithNoWinAmount() {
        String id = newPendingId();

        Reply r = patch(id, CANCEL);

        assertThat(r.status()).as(r.body()).isEqualTo(200);
        assertThat(r.json().get("status").asText()).isEqualTo("CANCELLED");
        assertThat(r.json().get("winAmount").isNull()).isTrue();
    }

    @Test
    void explicitNullWinAmountIsAcceptedOnCancel() {
        Reply r = patch(newPendingId(), "{\"status\":\"CANCELLED\",\"winAmount\":null}");

        assertThat(r.status()).as(r.body()).isEqualTo(200);
    }

    @Test
    void completedTransactionIsFinal() {
        String id = newPendingId();
        assertThat(patch(id, COMPLETE).status()).isEqualTo(200);

        assertThat(patch(id, CANCEL).status()).isEqualTo(409);
        assertThat(patch(id, "{\"status\":\"COMPLETED\",\"winAmount\":99.00}").status()).isEqualTo(409);

        var stored = get("/transactions/" + id).json();
        assertThat(stored.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(stored.get("winAmount").decimalValue()).isEqualByComparingTo("12.50");
    }

    @Test
    void cancelledTransactionIsFinal() {
        String id = newPendingId();
        assertThat(patch(id, CANCEL).status()).isEqualTo(200);

        Reply r = patch(id, COMPLETE);

        assertThat(r.status()).isEqualTo(409);
        assertThat(r.contentType()).startsWith("application/problem+json");
        var stored = get("/transactions/" + id).json();
        assertThat(stored.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(stored.get("winAmount").isNull()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"status\":\"COMPLETED\"}",
        "{\"status\":\"COMPLETED\",\"winAmount\":-1.00}",
        "{\"status\":\"COMPLETED\",\"winAmount\":1.005}",
        "{\"status\":\"CANCELLED\",\"winAmount\":5.00}",
        "{\"status\":\"PENDING\"}",
        "{\"status\":\"BOGUS\"}",
        "{\"winAmount\":5.00}",
        "{}",
        "not json"
    })
    void invalidPatchBodyReturns400AndLeavesTransactionPending(String payload) {
        String id = newPendingId();

        Reply r = patch(id, payload);

        assertThat(r.status()).as(r.body()).isEqualTo(400);
        assertThat(r.contentType()).startsWith("application/problem+json");
        assertThat(get("/transactions/" + id).json().get("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void bodyValidationTakesPrecedenceOverTransactionState() {
        String id = newPendingId();
        assertThat(patch(id, CANCEL).status()).isEqualTo(200);

        assertThat(patch(id, "{\"status\":\"COMPLETED\"}").status()).isEqualTo(400);
    }

    @Test
    void unknownTransactionReturns404() {
        Reply r = patch(UUID.randomUUID().toString(), COMPLETE);

        assertThat(r.status()).isEqualTo(404);
        assertThat(r.contentType()).startsWith("application/problem+json");
    }

    @Test
    void malformedIdReturns400() {
        assertThat(patch("not-a-uuid", COMPLETE).status()).isEqualTo(400);
    }
}
