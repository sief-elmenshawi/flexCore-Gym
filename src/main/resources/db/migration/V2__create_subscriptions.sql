CREATE TABLE subscription_plans (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    price NUMERIC(10,2) NOT NULL,
    duration_in_days INT NOT NULL,
    max_family_members INT
);

CREATE TABLE family_groups (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL REFERENCES users(id),
    plan_id BIGINT NOT NULL REFERENCES subscription_plans(id)
);

CREATE TABLE subscriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    plan_id BIGINT NOT NULL REFERENCES subscription_plans(id),
    status VARCHAR(20) NOT NULL,
    start_date TIMESTAMP,
    end_date TIMESTAMP,
    frozen_at TIMESTAMP,
    frozen_until TIMESTAMP,
    family_group_id BIGINT REFERENCES family_groups(id),
    created_by BIGINT,
    created_date TIMESTAMP,
    last_modified_by BIGINT,
    last_modified_date TIMESTAMP
);
