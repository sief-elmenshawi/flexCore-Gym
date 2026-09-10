-- Postgres does not auto-index FK columns, so every join/lookup below was a
-- sequential scan: growing linearly with table size. One index per hot path:
CREATE INDEX idx_subscriptions_user_id ON subscriptions (user_id);
CREATE INDEX idx_class_bookings_user_id ON class_bookings (user_id);
CREATE INDEX idx_class_bookings_class_id ON class_bookings (class_id);
CREATE INDEX idx_attendance_user_id ON attendance (user_id);
CREATE INDEX idx_pt_sessions_trainer_status_scheduled ON pt_sessions (trainer_id, status, scheduled_at);
CREATE INDEX idx_payments_subscription_id ON payments (subscription_id);
CREATE INDEX idx_payments_status_paid_at ON payments (status, paid_at);