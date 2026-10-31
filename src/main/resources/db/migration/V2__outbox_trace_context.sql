-- W3C traceparent of the request that wrote the row, so the publisher's Kafka send
-- joins the same trace even though it runs later on another thread.
ALTER TABLE outbox_events ADD COLUMN trace_parent VARCHAR(55);
