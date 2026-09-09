-- Stores the W3C traceparent captured when the event was recorded, so the outbox
-- publisher (running on a background scheduler thread, not in the request thread)
-- can re-inject it into the RabbitMQ message. Without this the async boundary would
-- break the distributed trace: the consumer would start a brand-new trace.
ALTER TABLE outbox_events ADD COLUMN trace_context VARCHAR(255);