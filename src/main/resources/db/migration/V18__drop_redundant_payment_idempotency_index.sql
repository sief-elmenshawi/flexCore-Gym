-- V15's explicit ix_payment_idempotency_key is fully redundant: the
-- UNIQUE(user_id, idempotency_key) constraint already creates an index over
-- the exact same columns. It is dead weight on every INSERT/UPDATE of
-- payment_idempotency, so drop it.
DROP INDEX IF EXISTS ix_payment_idempotency_key;