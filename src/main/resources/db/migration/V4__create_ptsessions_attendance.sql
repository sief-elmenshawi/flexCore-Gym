CREATE TABLE pt_sessions (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES users(id),
    trainer_id BIGINT NOT NULL REFERENCES users(id),
    scheduled_at TIMESTAMP NOT NULL,
    duration_minutes INT NOT NULL,
    status VARCHAR(20) NOT NULL
);

CREATE TABLE attendance (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    check_in_at TIMESTAMP NOT NULL DEFAULT now(),
    checked_in_by BIGINT NOT NULL REFERENCES users(id),
    subscription_id BIGINT NOT NULL REFERENCES subscriptions(id)
);
