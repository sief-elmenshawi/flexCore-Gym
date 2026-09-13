-- Subscription purge becomes a soft delete so payment records (the financial
-- audit trail) are always preserved. deleted_at marks the row as gone from
-- every user-facing query without destroying the payment history.
ALTER TABLE subscriptions ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX ix_subscriptions_user_deleted ON subscriptions(user_id, deleted_at);