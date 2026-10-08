-- OutboxPublisher looks for unpublished rows and OutboxCleanup deletes old published ones;
-- both filter on published_at.
CREATE INDEX idx_outbox_events_published_at ON outbox_events (published_at);
