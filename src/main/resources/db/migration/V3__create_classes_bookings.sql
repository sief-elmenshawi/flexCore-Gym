CREATE TABLE gym_classes (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    trainer_id BIGINT NOT NULL REFERENCES users(id),
    capacity INT NOT NULL,
    booked_count INT NOT NULL DEFAULT 0,
    starts_at TIMESTAMP NOT NULL,
    duration_minutes INT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_date TIMESTAMP,
    last_modified_by BIGINT,
    last_modified_date TIMESTAMP
);

CREATE TABLE class_bookings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    class_id BIGINT NOT NULL REFERENCES gym_classes(id),
    status VARCHAR(20) NOT NULL,
    booked_at TIMESTAMP NOT NULL DEFAULT now()
);
