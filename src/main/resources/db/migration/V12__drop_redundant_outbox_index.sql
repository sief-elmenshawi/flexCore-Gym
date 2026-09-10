-- V10's partial index (idx_outbox_events_pending_due) already covers the exact
-- findPendingEvents predicate (status = 'PENDING' AND next_attempt_at due, ORDER BY id)
-- with a narrower, staying-tiny index. The V9 composite (status, next_attempt_at, id)
-- is now dead weight on every INSERT/UPDATE of outbox_events, so drop it.
DROP INDEX IF EXISTS idx_outbox_events_status_next_attempt_id;