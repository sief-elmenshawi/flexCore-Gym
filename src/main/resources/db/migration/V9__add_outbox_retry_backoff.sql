ALTER TABLE outbox_events ADD COLUMN next_attempt_at TIMESTAMP;

-- NULL next_attempt_at means "retry now", so the composite index serves both the
-- status filter and the retry-due filter (status, next_attempt_at, id).
CREATE INDEX idx_outbox_events_status_next_attempt_id
    ON outbox_events (status, next_attempt_at, id);