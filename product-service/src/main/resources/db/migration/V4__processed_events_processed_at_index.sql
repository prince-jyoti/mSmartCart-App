-- ProcessedEventCleanup deletes old rows by processed_at.
CREATE INDEX idx_processed_events_processed_at ON processed_events (processed_at);
