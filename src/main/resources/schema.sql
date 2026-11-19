CREATE TABLE IF NOT EXISTS transactions (
    id          UUID                     PRIMARY KEY,
    round_id    VARCHAR(255)             NOT NULL,
    player_id   VARCHAR(255)             NOT NULL,
    game_name   VARCHAR(255)             NOT NULL,
    bet_amount  NUMERIC(19, 2)           NOT NULL,
    win_amount  NUMERIC(19, 2),
    currency    CHAR(3)                  NOT NULL,
    status      VARCHAR(16)              NOT NULL,
    created_at  TIMESTAMP(9) WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_transactions_player ON transactions (player_id);
CREATE INDEX IF NOT EXISTS ix_transactions_order ON transactions (created_at, id);

-- The primary key is the concurrency guard: exactly one request can claim a key.
CREATE TABLE IF NOT EXISTS idempotency_keys (
    idempotency_key VARCHAR(255)   PRIMARY KEY,
    round_id        VARCHAR(255)   NOT NULL,
    player_id       VARCHAR(255)   NOT NULL,
    game_name       VARCHAR(255)   NOT NULL,
    bet_amount      NUMERIC(19, 2) NOT NULL,
    currency        CHAR(3)        NOT NULL,
    transaction_id  UUID           NOT NULL REFERENCES transactions (id),
    response_status INT            NOT NULL,
    response_body   VARCHAR(8000)  NOT NULL
);
