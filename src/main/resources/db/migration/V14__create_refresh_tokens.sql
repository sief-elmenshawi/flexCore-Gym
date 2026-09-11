CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    replaced_by_token_id BIGINT REFERENCES refresh_tokens(id),
    created_date TIMESTAMP NOT NULL
);

CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens(user_id);