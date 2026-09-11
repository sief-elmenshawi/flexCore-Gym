CREATE TABLE payment_idempotency (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    idempotency_key VARCHAR(64) NOT NULL,
    payment_id BIGINT REFERENCES payments(id),
    created_date TIMESTAMP NOT NULL,
    UNIQUE(user_id, idempotency_key)
);

CREATE INDEX ix_payment_idempotency_key ON payment_idempotency(user_id, idempotency_key);