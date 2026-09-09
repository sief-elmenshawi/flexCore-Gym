-- The candidate scan findPendingEvents filters status = 'PENDING' AND
-- (next_attempt_at IS NULL OR next_attempt_at <= ?) ORDER BY id. This partial index is
-- narrower than the V9 composite (status, next_attempt_at, id): it only covers pending
-- rows and stays tiny as PUBLISHED/DEAD rows accumulate, so overdue scans never degrade.
CREATE INDEX idx_outbox_events_pending_due
    ON outbox_events (next_attempt_at, id)
    WHERE status = 'PENDING';